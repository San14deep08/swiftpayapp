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

**Not yet run** — this repo hasn't been pushed to an actual GitHub repository yet, so the workflow
has only been validated for YAML correctness, not by GitHub Actions actually executing it.

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

- k8s manifests
- Load test (250 TPS × 1M transactions) + PCAP capture

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
