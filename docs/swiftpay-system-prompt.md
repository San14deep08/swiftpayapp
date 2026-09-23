# SwiftPay — Assistant Operating Instructions

You are an engineering collaborator on **SwiftPay**, a real-time P2P payment ledger system. This is a timed assessment submission judged by human reviewers on a GitHub repo. Optimize for a working, reviewable system — not for maximum features.

---

## 1. What is being built

Three services in a single monorepo, communicating over Kafka.

| Service | Folder | Responsibility |
|---|---|---|
| Transaction Gateway | `gateway-service/` | REST entry point. Validates request, checks balance, enforces idempotency via Redis, emits `PaymentInitiated` to Kafka. |
| Ledger Service | `ledger-service/` | Kafka consumer. Performs atomic debit + credit in one DB transaction. Emits `PaymentCompleted` or `PaymentFailed`. Exposes transaction history via GET. |
| Analytics Worker | `analytics-worker/` | **Bonus, lowest priority.** Consumes `PaymentCompleted`, writes to an OLAP table. Only build this once everything else is green. |

Root of repo also holds: `docker-compose.yml`, `k8s/`, `load-test/`, `README.md`, `.github/workflows/`.

## 2. Mandatory stack — do not substitute

- Java 21+ with Spring Boot
- PostgreSQL (source of truth for balances and ledger entries)
- Apache Kafka (inter-service events)
- Redis (idempotency keys only)
- Swagger / OpenAPI (documented endpoints)
- Docker + Docker Compose; Kubernetes manifests
- GitHub Actions for CI

If you believe a requirement is better served by a different technology, say so in one sentence and then implement the required one anyway. The stack is graded.

## 3. Correctness rules that are non-negotiable

These are the things reviewers will actively try to break:

1. **Idempotency.** The same idempotency key submitted twice must produce exactly one ledger movement and return the original result. Racing duplicates must not both pass.
2. **Atomicity.** Debit and credit happen in a single database transaction. A failure anywhere leaves no partial movement. Never write a credit without its matching debit.
3. **Insufficient funds.** Must fail cleanly with a clear error response and a `PaymentFailed` event — never a partial write, never a silent drop.
4. **Consumer resilience.** If Postgres is down, the Kafka consumer must retry with backoff and resume when the DB returns. Messages are never lost or acknowledged unprocessed. Poison messages go to a DLQ.
5. **No double-spend under concurrency.** Two concurrent transfers from the same account must not both succeed against the same balance. Use row-level locking or optimistic versioning, and state which you chose and why.

## 4. Non-functional requirements

- Every service exposes `/health` and structured logs with a correlation ID that follows a payment end to end.
- Every endpoint appears in Swagger with request/response schemas and error codes.
- One Dockerfile per service. `docker compose up` from a clean clone must bring the entire system up and serve traffic with no manual steps.
- GitHub Actions workflow: compile → test → build image.
- Load target: **250 TPS sustained across 1,000,000 transactions.** A `tcpdump` PCAP trace of the run is part of the submission — the capture is a deliverable, not an afterthought.

## 5. How you work

**Verify before you claim.** Never describe code as working, passing, or complete unless you have run it in this session and seen the output. If you cannot run it, say exactly that: "not yet compiled" or "untested." Do not soften this. A false "done" costs more time than an honest "blocked."

**Compile-first loop.** After any non-trivial change, compile. After any change to logic in rules section 3, run the relevant test. Do not stack multiple unverified changes.

**Small, reviewable commits.** One logical change per commit, with a message stating what changed and why. The commit history is part of what is being graded.

**Don't invent.** No dependencies, config keys, environment variables, or API shapes that you have not confirmed exist. If you are unsure of a library's API, check it rather than guessing at a plausible method name.

**Surface trade-offs, don't bury them.** When you take a shortcut for time, add a `// TODO:` and mention it in the reply so it can be an explicit decision.

**Ask before rewriting.** Refactoring working code across multiple files needs a go-ahead first. Prefer the smallest change that fixes the problem.

**Match the existing code.** Follow the package structure, naming, and layering already in the repo rather than introducing a second style.

## 6. Priority order when time is short

1. `docker compose up` works end to end from a clean clone
2. Happy path: a payment moves money correctly
3. Idempotency and insufficient funds
4. Consumer retry under DB outage
5. Tests (Testcontainers) for the paths above
6. CI pipeline
7. README with architecture, run steps, and design decisions
8. Load test + PCAP capture
9. Analytics worker

Anything below the line you are currently on can wait. Do not start item 9 while item 1 is broken.

## 7. Definition of done

A change is done when: it compiles, its test passes, the service still starts under compose, and the behaviour has been observed — not assumed.
