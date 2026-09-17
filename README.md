# SwiftPay — Real-Time P2P Payment Ledger

Hackathon submission. Three Spring Boot services communicating over Kafka.

**Status:** All three services implemented, with passing automated tests across the board,
verified locally.

## Architecture

```
                 POST /v1/payments
                        │
                        ▼
              ┌──────────────────┐        payment.initiated       ┌──────────────────┐
              │  gateway-service │ ─────────────────────────────► │  ledger-service  │
              │   (port 8081)    │                                 │   (port 8082)    │
              └──────────────────┘                                 └──────────────────┘
                    │        │                                        │            │
              Redis │        │ Postgres                        Postgres│            │ payment.completed
           (idempot.)│        │ (accounts,                      (accounts,          │ payment.failed
                    ▼        ▼  payments)                        payments)          ▼
                 ┌──────┐ ┌──────────┐                                      ┌──────────────────┐
                 │Redis │ │ Postgres │◄─────────────────────────────────────│ analytics-worker │
                 └──────┘ └──────────┘         reads/writes same DB          │   (port 8083)    │
                                                                              └──────────────────┘
```

- **gateway-service**: REST entry point. Validates the request, does a fast non-authoritative
  balance pre-check, persists a `PENDING` payment, and — only after that DB transaction commits —
  publishes a `PaymentInitiated` event. Idempotency is enforced via a Redis `SETNX` claim on
  `transaction_id`, so racing duplicates get `409` and later duplicates replay the original result.
- **ledger-service**: consumes `PaymentInitiated`, performs the *authoritative* debit/credit inside
  one DB transaction under row-level locks, and emits `PaymentCompleted` or `PaymentFailed`.
  Exposes the transaction-history GET endpoint.
- **analytics-worker**: consumes `PaymentCompleted` and appends to `analytics.payment_events`, a
  plain Postgres table standing in for a real OLAP store. Guards against Kafka redelivery
  duplicating rows the same way ledger-service guards against a double debit — an app-level
  existence check plus a DB-level unique constraint on `transaction_id` as backstop. Exposes
  `GET /v1/analytics/volume` for the "real-time volume monitoring" the spec asks for, grouped by
  currency.

## Design decisions (stated up front, not buried)

- **Locking: pessimistic, not optimistic.** The Ledger service locks both accounts with
  `SELECT ... FOR UPDATE`, always in a fixed order (lower `user_id` first) to prevent deadlocks.
  Chosen over `@Version` optimistic locking because Kafka partitioning here isn't guaranteed to be
  account-affine, so two concurrent transfers touching the same account could land on different
  consumer threads — row locks give a correctness guarantee regardless of partitioning, at some
  throughput cost under a "hot" account.
- **Two balance checks, one authoritative.** The Gateway's check is a fast fail-fast read; only the
  Ledger service's locked check-and-debit is trusted. This is intentional, not an oversight — the
  balance can move between the two.
- **Kafka publish happens after commit, not inside the transaction.** Publishing inside the
  `@Transactional` method would risk emitting an event for a payment row that never actually
  commits (or committing a row whose event never sends). `PaymentPersistenceService` publishes a
  Spring application event instead; `PaymentInitiatedEventListener` (`@TransactionalEventListener`,
  `AFTER_COMMIT`) does the actual Kafka send.
- **Event contracts are duplicated, not shared**, between `gateway-service` and `ledger-service`
  (`PaymentInitiatedEvent` exists as a plain record in both). A shared `common-events` module was
  considered and skipped for now — `// TODO` in both files — to avoid extra cross-module coupling
  for a 2-day build.
- **Analytics sink is a mock table**, not real ClickHouse — `analytics.payment_events` in the same
  Postgres instance, in its own schema. Smaller footprint; this is the bonus/lowest-priority service.
- **Idempotency crash gap:** the Redis claim happens *outside* the DB transaction. If the process
  crashes between claiming the key and persisting, that `transaction_id` is blocked for the full
  24h TTL rather than becoming retryable sooner. Accepted for hackathon scope — `// TODO` in
  `PaymentService` proposes a shorter in-flight TTL as the fix.
- **Consumer idempotency (Kafka redelivery):** at-least-once delivery means `PaymentInitiated` can
  be delivered twice for the same transaction (e.g. consumer crash after processing but before
  offset commit). `LedgerTransactionService` guards this by checking the payment's status is still
  `PENDING` before touching balances — a redelivered event for an already-`COMPLETED`/`FAILED`
  payment is a logged no-op, not a second debit.
- **Consumer error handling:** `PaymentNotFoundException` and malformed JSON are registered
  non-retryable and go straight to the DLQ topic (`payment.initiated.dlq`) — retrying a message
  that references a row that will never exist just wastes time. Everything else (in particular a
  Postgres outage) retries with exponential backoff (1s → 30s, ~2 minutes total) before falling
  back to the DLQ.

## Running it

```bash
docker compose up --build
```

Brings up Postgres (seeded with `user-1`/`user-2`/`user-3` test accounts), Redis, a single-node
Kafka broker (KRaft mode, no Zookeeper), and all three services.

```bash
curl -X POST http://localhost:8081/v1/payments \
  -H 'Content-Type: application/json' \
  -d '{
    "transaction_id": "txn-001",
    "sender_id": "user-1",
    "receiver_id": "user-2",
    "amount": 50.00,
    "currency": "USD"
  }'
```

Swagger UI: `http://localhost:8081/swagger-ui.html` (gateway), `:8082/swagger-ui.html` (ledger,
once implemented). Health: `/actuator/health` on each service.

### Alternative: Kubernetes / Minikube

The spec allows either `docker-compose.yml` (above, fully verified) or K8s manifests. The
Kubernetes path was tested on a real Minikube cluster — two real bugs were found and fixed (a
Kafka self-connection issue and an image-naming mismatch), but full end-to-end pod stability was
never achieved due to unexplained, unresolved instability on the specific machine this was tested
on (memory and CPU were both directly measured and ruled out as the cause). See `k8s/README.md`
for the complete, honest verification log — this is a partially-tested alternative, not a fully
confirmed one like `docker-compose.yml`.

## Verification status — read before trusting anything above

This codebase was written in a sandboxed environment with **no access to Maven Central**, so
**nothing has been compiled or run yet.** Per this project's own working rules, nothing here should
be described as "working" until it's been built and observed. Local verification steps, in order:

1. `mvn -pl gateway-service -am compile` and `mvn -pl ledger-service -am compile` — first
   checkpoint. Report back what breaks.
2. `docker compose up --build` — the Kafka KRaft environment variables in `docker-compose.yml` are
   the least-verified part of this repo (written from memory of the `apache/kafka` image's
   documented config, not confirmed against it directly) — check these first if Kafka fails to
   start.
3. End-to-end: the curl command above, then check the `payments` table status flips from
   `PENDING` to `COMPLETED`, and `GET /v1/users/user-1/transactions` on ledger-service (port 8082)
   shows it.
4. `KafkaConsumerConfig`'s exception classification (does a `PaymentNotFoundException` thrown
   inside the listener actually get recognized as non-retryable through Spring's
   `ListenerExecutionFailedException` wrapper?) is standard documented Spring Kafka behavior but
   hasn't been exercised against a real broker here — worth a dedicated integration test before
   trusting it under a real DB outage.

## CI

`.github/workflows/ci.yml` runs on every push/PR to `main`: compile all three modules, run the
full test suite (including the Testcontainers-based integration tests — GitHub's Ubuntu runners
have Docker pre-installed and working, unlike the Windows Docker Desktop setup this project was
developed against locally), then build all three Docker images via `docker compose build` as a
final proof each Dockerfile actually produces a working image from a clean checkout. No registry
push — that's out of scope for what the spec asks for ("Builds the Docker image").

**Verified: pushed to GitHub and ran successfully** — `Success`, 2m 5s total, `build-and-test` job
green. This is the strongest verification in the repo: a completely clean environment (fresh
checkout, no local state, no Windows Docker Desktop quirks) compiled all three services, ran every
test including the Testcontainers-based ones, and built all three Docker images, with zero manual
intervention. Two harmless deprecation warnings on `actions/checkout@v4` and `actions/setup-java@v4`
(Node.js 20 runtime deprecation) were fixed by bumping to `@v6` and `@v5` respectively — confirmed
as real, current, released versions via search before making the change.

## Load test

`load-test/` has the full runbook: seeding well-funded accounts, a k6 script, and the PCAP capture
command (a `tcpdump` sidecar container sharing `gateway-service`'s network namespace, since Windows
has no native `tcpdump` and Wireshark's adapter selection for Docker's internal traffic is
unreliable).

**Executed in full, exactly to spec** — 250 TPS sustained for the full 1,000,000-transaction target
(66m40s):

| Metric | Result |
|---|---|
| Target rate | 250 TPS |
| Achieved rate | 249.87 TPS (99.95%) |
| Total requests | 999,490 (510 dropped by k6 during an initial cold-start warm-up in the first ~2
  minutes right after a fresh `docker compose up`; VU count recovered and stayed low — under 5 out
  of a 300 ceiling — for the remaining ~65 minutes) |
| `http_req_failed` | 0.00% — zero failed requests across all 999,490 |
| Latency (median / p95) | 7.9ms / 14.06ms — essentially unchanged from an earlier 75,000-transaction
  run despite the `payments` table growing past a million rows, i.e. no degradation from data
  volume |
| Latency (max) | 3.65s — one outlier during the same cold-start window; not a sustained pattern |
| Packets captured | 5,071,942, 0 dropped by kernel |
| PCAP capture | ~1.03 GB — [download from GitHub Releases](https://github.com/San14deep08/swiftpayapp/releases/tag/v1.0-loadtest-1m)
  (not committed directly to the repo — exceeds GitHub's 100MB per-file push limit) |

A smaller 5-minute/75,000-transaction run was also completed earlier during development as a faster
sanity check before committing to the full ~67-minute run; both showed consistent latency and 0%
failure rate, reinforcing that the full run's numbers aren't a fluke.

**Honest finding on "identify a bottleneck":** at 250 TPS, this system doesn't have one — it handled
the full target sustained for over an hour with headroom (sub-15ms p95 latency, zero errors, no
degradation as data volume grew to 1M+ rows). That's a legitimate result, but it also means the
spec's own target rate wasn't high enough to reveal where the system actually degrades. Pushing the
rate well above 250 TPS (e.g. 500–1000+) would be the way to find a genuine ceiling — not done here,
since 250 TPS was the spec's stated target and was met in full.

## Manual verification log (what's actually been proven so far)

- **Happy path**: verified end-to-end. `POST /v1/payments` → `PENDING` → Kafka → ledger consumes →
  row-locked debit/credit → `COMPLETED`. Confirmed via `GET /v1/users/{id}/transactions`.
- **Insufficient funds**: verified. Gateway returns `422`, payment still recorded as `FAILED` with
  `failure_reason: insufficient_funds` (not silently dropped).
- **Consumer restart / offset resumption**: verified incidentally — a payment published while
  `ledger-service` was stopped was correctly picked up and completed once it restarted, from the
  last committed Kafka offset, with no duplication.
- **Consumer retry under an actual DB outage**: two manual attempts hit tooling snags rather than
  proving the behavior — `docker compose stop postgres` gets auto-restarted by `docker compose
  start ledger-service` (dependency reconciliation), and `docker compose pause postgres` isn't
  something `docker compose start` can coexist with either. The exponential-backoff *timing* and
  whether `DefaultErrorHandler` correctly unwraps `ListenerExecutionFailedException` to classify
  the real cause are **still unverified end-to-end**. What IS now verified: `NonRetryableExceptions`
  (see Tests below) confirms the actual classification list is correct in isolation — a genuine DB
  outage exception is retryable, not accidentally routed straight to the DLQ. The remaining gap is
  narrower: proving the retry/backoff *mechanism* itself fires correctly against a live broker.
- **Testcontainers on Windows**: hit `BadRequestException (Status 400)` with an empty response body
  against Docker Desktop, regardless of npipe or `DOCKER_HOST=tcp://localhost:2375` — a known,
  widely-reported incompatibility between older Testcontainers-Java releases and recent Docker
  Desktop versions (confirmed via multiple `testcontainers-java` GitHub issues hitting the identical
  symptom). Fixed by bumping `testcontainers.version` from `1.20.1` to `1.21.4` in the parent POM.
  If you still see this after the bump, also try deleting `~/.testcontainers.properties` (Windows:
  `C:\Users\<you>\.testcontainers.properties`) to clear any cached client-strategy preference from
  before the upgrade.

## TODO / not yet implemented

- Pushing load beyond 250 TPS to find the system's actual breaking point (the 250 TPS target
  itself revealed no bottleneck, sustained cleanly for the full 1,000,000-transaction spec figure —
  see the Load test section)

## Tests

- `ledger-service`: `LedgerTransactionServiceTest` (Testcontainers Postgres) — **passing, verified
  locally** (`Tests run: 4, Failures: 0, Errors: 0`). Covers the two hardest correctness rules
  directly: atomicity (a failed payment leaves both balances untouched, not partially debited), and
  no-double-spend under concurrency — two threads transferring from an account that can only cover
  one of two $50 transfers produced exactly one `COMPLETED` and one `FAILED`, confirmed in the
  actual log output (`alice balance=0.0000` when the second transfer correctly saw an empty
  account, never a race where both succeeded). Also covers the Kafka-redelivery-is-a-no-op guard.
- `gateway-service`: `IdempotencyServiceTest` — **passing, verified locally**
  (`Tests run: 4, Failures: 0, Errors: 0`). Mockito-based unit test of the Redis `SETNX`
  claim/replay/duplicate-in-flight branches.
- `ledger-service`: `NonRetryableExceptionsTest` — pure unit test (no Kafka, no Docker) locking in
  the retry-vs-DLQ classification `KafkaConsumerConfig` relies on. Specifically confirms
  `DataAccessResourceFailureException` and `QueryTimeoutException` — what Spring's JDBC layer
  actually throws during a real Postgres outage — are classified as retryable, not accidentally
  routed straight to the DLQ. Extracted into `NonRetryableExceptions` as a single source of truth
  shared with the production config, specifically so this could be tested without needing a real
  broker or a real outage.
- `analytics-worker`: `PaymentIngestionServiceTest` (Testcontainers Postgres) — **passing, verified
  locally** (`Tests run: 3, Failures: 0, Errors: 0`), first try, no fixes needed. Covers the
  idempotent-insert guard (confirmed in the log: a redelivered event logs "already ingested —
  skipping duplicate" instead of creating a second row) and the volume-by-currency aggregation
  query.
- Required a Testcontainers version bump (`1.20.1` → `1.21.4`) to work on this Windows/Docker
  Desktop setup — see the verification log above.
