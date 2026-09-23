# SwiftPay — SDLC Workflow Instructions

Follow this workflow in order. Each phase has a deliverable and a **gate**. At a gate, stop, show me the deliverable, and wait for my approval before starting the next phase. Do not run ahead.

Gates are quick reviews, not formal sign-off — this is a timed assessment. If I say "approved" or "go", move on. If I say nothing, wait.

Write every design deliverable as a markdown file under `docs/`. These files are part of the submission and get committed.

---

## Phase 0 — Requirements analysis

**Input:** the requirement document in the project context. Read it in full before doing anything else. Do not rely on your summary of it from an earlier session; re-read it.

**Work:**
- Extract every requirement into a numbered list. Give each an ID: `FR-01`, `FR-02` for functional, `NFR-01` for non-functional, `CON-01` for constraints (mandatory tech, deadlines, submission format).
- Mark each as **Explicit** (stated in the document) or **Inferred** (you concluded it). Never blur the two.
- Separately list every **ambiguity** — anything the document leaves open, contradicts itself on, or assumes. Phrase each as a direct question with your recommended default.
- Separately list every **submission artifact** the document demands (repo, trace files, docs, screenshots, video). These are the easiest marks to lose.

**Deliverable:** `docs/01-requirements.md`

**Gate:** I answer your ambiguity questions before you design anything.

---

## Phase 1 — System design (HLD)

**Work:**
- Service boundaries and why each exists. Justify against the requirement IDs.
- Component diagram in Mermaid: services, Kafka topics, Postgres, Redis, external entry points.
- Sequence diagram in Mermaid for the primary flow, end to end, including the event hops.
- Sequence diagram for the two most important failure paths.
- Event catalogue: every topic, its producer, its consumers, its payload schema, its partition key, and the ordering guarantee it needs.
- Key design decisions, each as: decision, alternatives considered, why, trade-off accepted. Concurrency control on balances belongs here.

**Deliverable:** `docs/02-system-design.md`

**Gate:** approve before schema work.

---

## Phase 2 — Data model / schema design

**Work:**
- Logical model first: entities, attributes, relationships, cardinality.
- Then physical DDL per table: columns, types, precision (money is exact numeric — never floating point), nullability, defaults.
- Constraints: primary keys, foreign keys, unique constraints, check constraints. State which invariant each one protects.
- Indexes, each with the query it serves. No speculative indexes.
- Idempotency key storage: where it lives, its TTL, and what happens on a hit.
- Migration files in the project's migration tool, versioned and forward-only.
- Explicitly state the isolation level and locking strategy for the balance update, and show the exact SQL.

**Deliverable:** `docs/03-data-model.md` plus migration files

**Gate:** approve before API design.

---

## Phase 3 — API contract

**Work:**
- OpenAPI spec written before the controllers, not generated from them.
- Every endpoint: path, method, request schema, response schema, all error responses with codes and meanings.
- Idempotency header contract: name, format, client responsibility, server behaviour on repeat.
- A consistent error response shape used everywhere.

**Deliverable:** `docs/04-api-contract.md` and the OpenAPI file

**Gate:** approve before writing implementation code.

---

## Phase 4 — Implementation plan

**Work:**
- Break the build into tasks small enough to compile and verify individually.
- Order them by dependency, and within that by the priority order in the operating instructions.
- For each task: which requirement IDs it satisfies, and how you will verify it.
- Flag any task you expect to be risky or slow.

**Deliverable:** `docs/05-implementation-plan.md`

**Gate:** approve before coding.

---

## Phase 5 — Implementation

Work task by task from the approved plan. Per task: implement, compile, verify, commit, report.

Report honestly — "compiles, not yet tested" is a valid status. Do not batch several unverified tasks and present them as done.

If implementation reveals the design was wrong, stop. Say what the design got wrong and propose the amendment. Update the design doc. Do not silently diverge from an approved design — the docs are graded against the code.

**Gate:** after each task, a one-line status. After the last task, a full status against the requirement IDs.

---

## Phase 6 — Testing

**Work:**
- Test plan first: which requirement IDs are covered by which test, and at what level (unit, integration, end to end).
- Integration tests against real dependencies via Testcontainers, not mocks, for anything touching the DB or Kafka.
- Explicit tests for: idempotent replay, insufficient funds, concurrent transfers from one account, and consumer behaviour during a DB outage.
- Report actual pass/fail output. Never summarize a test run you did not execute.

**Deliverable:** `docs/06-test-plan.md` and passing tests

**Gate:** approve before DevOps work.

---

## Phase 7 — Packaging and CI

Dockerfiles, compose, Kubernetes manifests, GitHub Actions pipeline.

**Acceptance test for this phase:** clone the repo fresh into an empty directory, run the documented start command, and push a payment through. If that sequence needs a single undocumented manual step, the phase is not done.

**Gate:** approve before load testing.

---

## Phase 8 — Load test and capture

**Work:**
- Short dry run first to find the bottleneck. Report the limiting resource before attempting the full run.
- Verify the packet capture command produces a readable trace on the dry run, before the real run.
- Then the full run. Report throughput achieved, error rate, and latency percentiles — measured, not estimated.

**Deliverable:** `docs/07-load-test.md`, the raw results, and the capture file

---

## Phase 9 — Submission review

**Work:**
- Walk the requirement list from Phase 0 line by line. For each ID: satisfied, partially satisfied, or not done. No requirement is skipped in this table.
- Confirm every submission artifact from Phase 0 exists.
- README: what it is, architecture, how to run it, design decisions, known limitations.
- State the known gaps plainly rather than presenting the submission as complete.

**Deliverable:** `docs/08-submission-checklist.md` and a finished README

---

## Standing rules across all phases

- **Trace everything to a requirement ID.** If you cannot name the requirement a piece of work serves, ask whether it should be built at all.
- **Ask rather than assume.** An assumption recorded as a question costs a minute. An assumption buried in code costs a rebuild.
- **Never report unverified work as complete.** Say what you ran and what you saw.
- **Design docs and code stay in sync.** If one changes, the other changes in the same commit.
