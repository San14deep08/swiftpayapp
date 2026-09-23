# SwiftPay — AI-Assisted Engineering Playbook

**Project:** SwiftPay — Real-Time P2P Payment Ledger System
**Engineer:** Sandeep Kumar Mahto
**Document type:** AI Engineering Playbook and Prompt Playbook
**Version:** 1.0 — September 2026 *(adjust to your actual submission date)*
**Audience:** Technical review committee, engineering management, AI governance

---

## 1. Executive Summary

### Why AI was used

SwiftPay is a distributed, event-driven payment ledger spanning three services, five infrastructure components, and a set of correctness constraints — idempotency, atomic double-entry movement, ordered event processing — where mistakes are silent and expensive. The assessment permitted AI assistance explicitly. The constraint was time, not capability: a system of this shape normally takes a team a sprint, and the assessment window was measured in days.

AI was used to compress the mechanical cost of that work — scaffolding, boilerplate, configuration, first-draft test cases, documentation — so that engineering time could concentrate on the parts where judgment is not transferable: concurrency strategy on balance updates, failure semantics under partial outage, and verification that the system does what the requirements actually say.

### What AI accelerated

| Area | Effect |
|---|---|
| Project scaffolding and module structure | Hours reduced to minutes |
| Spring Boot configuration, Kafka and Redis wiring | Eliminated most reference-doc lookup |
| First-draft DTOs, mappers, repository interfaces | Near-complete generation, reviewed and adjusted |
| Test case enumeration | Broader edge-case coverage than unaided recall |
| Documentation drafting | Substantially faster first drafts |

**Deliberately left qualitative.** No time-tracking data exists to support a measured percentage, and stating one that can't be defended in a review conversation would cost more credibility than the qualitative table above provides on its own.

### What remained under human control

Architecture selection, data model and constraint design, the concurrency control strategy for balance mutation, failure-handling semantics, acceptance of every generated artifact, and full responsibility for the submitted system. No generated code entered the repository without being read, compiled, and exercised.

### How quality was maintained

Through a fixed verification protocol (Section 7) applied to every generated artifact, and phase gates (Section 5) at which work stopped for human decision before proceeding. AI output was treated as a proposal requiring evidence, never as a result.

### Positioning

> **AI accelerates execution; engineers own decisions and outcomes.**

This document does not claim that AI built SwiftPay. It documents a human-in-the-loop engineering workflow in which AI functioned as a high-throughput drafting and analysis tool under continuous developer validation.

---

## 2. AI-Assisted Engineering Philosophy

### Core principle

An AI coding assistant is a fast, confident, fallible collaborator with no accountability. It produces plausible output at a rate far exceeding a human's ability to read it carefully. That asymmetry — generation speed versus review speed — is the central risk in AI-assisted engineering, and the entire workflow below exists to manage it.

The response is not to generate less. It is to make acceptance expensive: nothing is considered done because it looks done.

### The working loop

Every unit of AI-assisted work followed the same eight-step cycle:

```
Prompt  →  Generate  →  Inspect  →  Execute  →  Test  →  Validate  →  Correct  →  Integrate
```

| Step | Owner | What happens |
|---|---|---|
| **Prompt** | Developer | Context, constraints, and expected output format specified before generation |
| **Generate** | AI | Produces a candidate artifact |
| **Inspect** | Developer | Read line by line; unfamiliar APIs and dependencies verified against real documentation |
| **Execute** | Developer | Compiled and run — the first point at which any claim about the code becomes checkable |
| **Test** | Both | AI drafts cases; developer executes them and reads the actual output |
| **Validate** | Developer | Behaviour compared against the requirement it is supposed to satisfy |
| **Correct** | Both | Defects fed back with evidence; fix re-enters the loop at Inspect |
| **Integrate** | Developer | Committed with a message stating what changed and why |

The loop has no exit that skips Execute. An artifact that has not been run has not been verified, regardless of how correct it appears on reading.

### Three operating rules

1. **Confidence is not evidence.** AI output carries no uncertainty signal. Fluent, well-structured code that invokes a method which does not exist reads identically to correct code. Only execution distinguishes them.

2. **The assistant optimises for plausibility, not correctness.** It will produce something that looks like a solution when it does not have one. This is most dangerous in exactly the areas that matter here — concurrency, transaction boundaries, and failure paths — because those are the areas where plausible and correct diverge most often, and where a defect is least visible in a code review.

3. **Ambiguity resolved by the AI is a defect in the prompt.** Where the assistant had to guess, the developer failed to specify. Every such guess was converted into an explicit decision rather than left implicit in code.

---

## 3. Development Context

### System under development

SwiftPay is an event-driven peer-to-peer payment ledger built as three services in a single repository.

| Service | Responsibility |
|---|---|
| **Transaction Gateway** | REST entry point. Request validation, balance precondition check, Redis-backed idempotency enforcement, emission of `PaymentInitiated` to Kafka. |
| **Ledger Service** | Kafka consumer. Atomic debit and credit within a single database transaction, emission of `PaymentCompleted` or `PaymentFailed`, transaction history query endpoint. |
| **Analytics Worker** | Consumes completed payments into an OLAP store. Scoped as optional. |

### Mandatory technology constraints

Java 21+ with Spring Boot · PostgreSQL · Apache Kafka · Redis · OpenAPI/Swagger · Docker and Docker Compose · Kubernetes manifests · GitHub Actions CI

These were fixed by the assessment specification and were not open to substitution. Where the assistant proposed an alternative, the constraint took precedence and the proposal was recorded rather than adopted.

### Correctness constraints treated as non-negotiable

1. Duplicate submission of an idempotency key produces exactly one ledger movement
2. Debit and credit are atomic — no partial movement survives a failure
3. Insufficient funds fails cleanly with a `PaymentFailed` event and no write
4. The Kafka consumer survives a database outage without message loss
5. Concurrent transfers from one account cannot both succeed against the same balance

### Scope note — frontend

**Not applicable.** SwiftPay is a backend system of three services with no user interface. Section 4.5 is retained as a structural placeholder and explicitly marked N/A rather than populated. Documenting frontend prompts for a system with no frontend would misrepresent the work.

### Non-functional targets

OpenAPI documentation on all endpoints · health endpoints and correlation-ID logging · single-command startup from a clean clone · CI pipeline compiling, testing, and building images · sustained load target of 250 TPS across 1,000,000 transactions with packet capture evidence

---

## 4. AI Prompt Playbook

Nine prompt categories were used across the lifecycle. Each is documented with its objective, usage point, context requirements, structure, expected output, validation method, a representative example, known failure modes, and corrective action.

---

### 4.1 Requirements Understanding

**Objective.** Convert an ambiguous prose specification into an enumerated, testable requirement set with ambiguities surfaced rather than silently resolved.

**When used.** Before any design or implementation work. The first activity of the project.

**Context supplied.** The full requirement document verbatim. No summary, no paraphrase — paraphrasing at this stage discards the exact wording that later becomes the acceptance criterion.

**Prompt structure.**
```
Role: requirements analyst
Input: [full requirement document]
Task: extract every requirement as a numbered, individually testable item
Classification: FR- / NFR- / CON- prefix per item
Mandatory distinction: mark each item Explicit (stated) or Inferred (concluded)
Separate output section: every ambiguity, as a direct question with a recommended default
Separate output section: every submission artifact the document demands
Constraint: do not resolve ambiguity yourself; surface it
Output format: markdown tables
```

**Expected output.** A numbered requirement register, an ambiguity list phrased as questions, and a submission-artifact checklist.

**Developer validation.** Each extracted requirement traced back to the source sentence in the original document. Items marked Inferred reviewed individually and either promoted to Explicit with a source reference, accepted as a documented assumption, or discarded. The ambiguity list was answered by the developer before design began.

**Example prompt.**
> Read the attached requirement document in full. Extract every requirement as a numbered item using FR- for functional, NFR- for non-functional, CON- for constraints. For each, mark Explicit if the document states it, or Inferred if you concluded it — do not blur these. Then, separately, list every ambiguity or contradiction you found, phrased as a direct question with your recommended default. Do not resolve ambiguities yourself. Finally, list every artifact the document says must be submitted.

**Common failure modes.**
- Inferred requirements presented with the same confidence as explicit ones, quietly expanding scope
- Requirements merged into compound items that cannot be individually tested
- Ambiguity resolved by assumption rather than surfaced
- Submission artifacts buried in narrative and missed entirely

**Corrective action.** The Explicit/Inferred distinction was made a mandatory output field. Compound requirements were split manually. The submission-artifact list was extracted as a separate pass rather than as a sub-section, because artifacts stated in passing were otherwise lost.

---

### 4.2 Architecture and Design

**Objective.** Generate design alternatives with explicit trade-offs, for human selection. Not to receive a design.

**When used.** After requirements were approved and ambiguities resolved.

**Context supplied.** The approved requirement register, the mandatory stack constraints, the throughput target, and the correctness constraints in Section 3.

**Prompt structure.**
```
Role: system architect
Context: [requirements + fixed stack + throughput target]
Task: propose N distinct approaches to [specific decision]
Per approach: mechanism, failure behaviour, throughput implication,
              operational complexity, what it forecloses
Constraint: do not recommend; present the comparison
Output: comparison table + open questions for the decision-maker
```

**Expected output.** Two or more genuinely distinct approaches with trade-offs stated, not one approach with cosmetic variants.

**Developer validation.** Each option evaluated against the correctness constraints in Section 3 and against operational cost. Selection made by the developer, with the rationale and the rejected alternatives recorded in the design document. The record of what was rejected and why is part of the deliverable — a design document that shows only the chosen path hides the reasoning that justifies it.

**Example prompt.**
> For the balance mutation in the ledger service, propose three concurrency control strategies. For each: the mechanism, behaviour under two concurrent transfers from the same account, throughput implication at 250 TPS, failure mode under contention, and operational complexity. Do not recommend one. Present a comparison table and list what I need to decide.

**Common failure modes.**
- Converging on one recommendation and presenting weakened alternatives as a formality
- Defaulting to conventional patterns without testing them against the stated constraints
- Omitting the operational cost of a design — what it takes to run, monitor, and debug
- Treating the throughput target as decorative rather than as a design input

**Corrective action.** Prompts were rewritten to prohibit recommendation. Where alternatives were returned as near-duplicates, the assistant was asked directly what a materially different approach would be. Operational cost was made a mandatory output field after early responses omitted it.

**Human ownership statement.** Every architectural decision in SwiftPay — service boundaries, event topology, concurrency control, transaction boundaries, failure semantics — was selected by the developer from presented alternatives. The assistant contributed comparison and articulation, not selection.

---

### 4.3 Database and Data Model

**Objective.** Produce a schema where invariants are enforced by the database rather than by application code.

**When used.** After design approval, before any persistence code.

**Context supplied.** Entity list from requirements, expected query patterns, throughput target, and the correctness constraints.

**Prompt structure.**
```
Role: data modeller
Context: [entities + access patterns + constraints]
Task: logical model, then physical DDL
Mandatory per constraint: state which invariant it protects
Mandatory per index: state the query that justifies it
Money: exact numeric types only; justify precision and scale
Output: DDL + rationale table
```

**Expected output.** Logical model, physical DDL, constraint rationale, justified indexes, migration files.

**Developer validation.** Every constraint tested by attempting the violation it prevents. An unexercised constraint is an assumption. Monetary column types inspected specifically — floating-point representation of currency is a defect class that no test suite catches by accident and no code review reliably spots. Index set reviewed against actual query plans rather than accepted on assertion.

**Example prompt.**
> Design the ledger schema. Entities: accounts, transactions, ledger entries, idempotency records. Requirements: double-entry movement must be atomic; an idempotency key must be usable exactly once; transaction history queried by account with date range. For every constraint state which invariant it protects. For every index state the query it serves. Monetary values must use exact numeric types — state the precision and scale and justify them. Provide DDL and forward-only migrations.

**Common failure modes.**
- Floating-point types proposed for monetary values
- Constraints described in prose but absent from the DDL
- Speculative indexes with no corresponding query
- Idempotency records with no expiry policy, growing without bound
- Nullable columns where the domain requires a value

**Corrective action.** Monetary types were specified in the prompt rather than left to the assistant. Every generated constraint was verified present in the DDL by inspection, not by trusting the accompanying description. Indexes without a named query were removed.

**Session note.** None of the failure modes above occurred in this project's actual schema-design step — monetary columns were specified as `NUMERIC(19,4)` from the first draft, and the constraint set was verified present in the generated `db/init.sql` by direct inspection. Listed here as documented common modes to check for, not as claimed incidents.

---

### 4.4 Backend Development

**Objective.** Generate implementation against an approved design, service by service, in units small enough to verify individually.

**When used.** After design and schema approval.

**Context supplied.** Approved design and schema, existing package structure and conventions, the specific requirement IDs the unit satisfies.

**Prompt structure.**
```
Role: implementing engineer on an existing codebase
Context: [design + schema + existing conventions]
Task: implement [single component]
Satisfies: [requirement IDs]
Constraints: follow existing package structure and naming;
             no new dependencies without stating them;
             no invented API methods
Error handling: [explicit expected behaviour]
Output: complete compilable code + list of assumptions made
```

**Expected output.** A single component, complete and compilable, with assumptions stated separately.

**Developer validation.** Compile. Run. Exercise the path. Inspect the log output and the resulting database state. For anything touching the correctness constraints, the failure path was exercised deliberately, not assumed to work because the success path did.

Transaction boundaries received specific scrutiny: in Spring, a `@Transactional` annotation that appears correct can be silently inert depending on invocation path and proxy configuration. Correct appearance and correct behaviour are unrelated here, and only observing the rollback confirms it.

**Example prompt.**
> Implement the idempotency check in the gateway service. It satisfies FR-04. Use the existing Redis configuration in `config/RedisConfig.java` and follow the package structure already in `gateway-service`. Behaviour: on a repeated idempotency key, return the original response without re-processing; two concurrent requests with the same key must not both proceed. State the TTL you chose and why. List any assumptions you made. Do not add dependencies without telling me.

**Common failure modes.**
- Invented methods on real library classes — syntactically valid, non-existent
- Dependencies introduced silently
- `@Transactional` applied where it has no effect due to self-invocation or proxy semantics
- Exceptions caught and logged without propagation, converting failures into silent success
- Generated code following a different structural convention from the surrounding codebase
- Race conditions in check-then-act sequences that pass single-threaded testing

**Corrective action.** Unfamiliar API calls were verified against documentation before acceptance. Dependency changes were checked by diffing the build file rather than trusting the report. Transactional behaviour was confirmed by forcing a rollback and observing the database state. Concurrency behaviour was tested with genuinely concurrent requests, since a race condition is invisible to sequential testing.

---

### 4.5 Frontend Development

**Not applicable to this project.**

SwiftPay is a backend system comprising three services with no user interface. This section is retained for structural completeness. It is deliberately not populated — generating frontend prompt documentation for a system with no frontend would misrepresent the work performed.

---

### 4.6 Testing

**Objective.** Use AI to enumerate test scenarios more exhaustively than unaided recall, then execute and verify them.

**When used.** Alongside implementation, and as a dedicated phase before packaging.

**Context supplied.** Requirement IDs, the implementation under test, and the correctness constraints.

**Prompt structure.**
```
Role: test engineer
Context: [component + requirements it satisfies]
Task: enumerate test scenarios before writing any test code
Categories: happy path, boundary, negative, concurrency, failure injection
Mandatory: state the requirement ID each scenario covers
Then: implement the approved scenarios using Testcontainers
Constraint: integration tests use real dependencies, not mocks
```

**Expected output.** A scenario list for review, then implemented tests for the approved subset.

**Developer validation.** Tests were executed and their real output read. This is the single most important control in the entire workflow, because a test suite is the one artifact whose failure mode is to provide false assurance. Specifically checked:

- That each test **fails when the behaviour it covers is broken.** A test that passes against deliberately broken code is worse than no test — it is a false negative that will be trusted.
- That assertions verify outcomes rather than the absence of exceptions
- That tests touching the database and Kafka run against real instances via Testcontainers, not mocks that encode the same misunderstanding as the implementation

**Example prompt.**
> Before writing code, enumerate test scenarios for the ledger service debit/credit operation. Cover: happy path, insufficient funds at the exact boundary, concurrent transfers from one account, database failure mid-transaction, and duplicate event delivery. For each, state the requirement ID and the expected observable outcome. I will approve the list before you implement anything.

**Common failure modes.**
- Tests asserting that no exception was thrown rather than that the correct outcome occurred
- Mocks configured to return exactly what the implementation expects, testing nothing
- Concurrency scenarios written sequentially, so the race is never triggered
- Tests that pass against broken implementations
- Boundary cases at the wrong boundary — testing zero where the requirement concerns exact balance equality

**Corrective action.** Scenario enumeration was separated from implementation so the list could be reviewed before code existed. Tests covering the correctness constraints were verified by deliberately breaking the implementation and confirming the test failed. Mock-based tests were replaced with Testcontainers for anything touching persistence or messaging.

---

### 4.7 Debugging

**Objective.** Generate ranked hypotheses for observed behaviour. Explicitly not to receive a fix.

**When used.** On any defect whose cause was not immediately evident.

**Debugging prompt pattern.**
```
Problem       → observed behaviour, stated precisely
Error / Logs  → actual output, verbatim, not summarised
Relevant Code → the code path involved
Expected      → what should happen instead
Constraints   → what has already been ruled out, and how
Ask           → ranked hypotheses, each with a distinguishing test
Verify        → developer runs the test; evidence decides
```

**Critical distinction.** AI output in debugging is a hypothesis, never a diagnosis. The assistant cannot observe the running system; it pattern-matches on the description provided. It will produce a confident cause for a symptom it has no information about, and that cause will be plausible. The value is in the enumeration of candidates, not in the ranking.

**Developer validation.** Each hypothesis was paired with a test that distinguishes it from the others, and the test was run. A hypothesis that cannot be distinguished by observation was discarded rather than adopted. A fix was applied only after the mechanism was confirmed by evidence — because a fix applied to an unconfirmed cause may coincidentally suppress the symptom while leaving the defect in place, which is materially worse than not fixing it.

**Example prompt.**
> The ledger consumer stops processing after a Postgres restart and does not resume. Logs: [verbatim output]. Consumer configuration: [code]. Expected: retry with backoff and resume when the database returns. Already ruled out: network partition — the container is reachable and I verified it with a direct connection. Give me ranked hypotheses. For each, state the specific observation that would confirm or eliminate it. Do not propose a fix yet.

**Common failure modes.**
- Confident single-cause diagnosis from insufficient information
- Fixes proposed before the cause is established
- Symptom suppression mistaken for resolution
- The most common cause of the symptom class proposed regardless of the specific evidence given

**Corrective action.** The prompt pattern explicitly requests hypotheses and forbids fixes in the first response. Every hypothesis was required to come with a distinguishing observation, which made untestable hypotheses easy to discard.

---

### 4.8 Code Review

**Objective.** Use AI as an additional review pass with a defined checklist, not as an approval authority.

**When used.** After a component was working, before integration.

**Prompt structure.**
```
Role: senior reviewer
Context: [code + intended behaviour + requirements]
Review against: correctness, SOLID, security, performance,
                maintainability, readability, error handling,
                duplication, API design
Per finding: category, severity, location, why it matters, suggested change
Constraint: do not rewrite the code; report findings
Explicit: state what you are uncertain about
```

**Developer validation.** Every finding assessed independently. AI review has two systematic biases requiring correction: it reports style preferences at the same severity as defects, and it does not reliably distinguish a genuine issue from a deviation from convention. Findings were triaged by the developer, and disagreement was resolved by reasoning about the specific code rather than by deference.

Findings relating to security and error handling were verified by testing the condition described, not accepted on the strength of the description.

**Example prompt.**
> Review this payment processing service against: correctness, SOLID, security, performance, maintainability, error handling, and API design. Intended behaviour: [description]. For each finding give category, severity, location, why it matters, and a suggested change. Do not rewrite the code. State explicitly what you are uncertain about.

**Common failure modes.**
- Style preferences reported as defects
- Genuine concurrency and transaction issues missed — the highest-value findings are the least reliable
- Security findings that are theoretical in the actual deployment context
- Findings stated with uniform confidence regardless of the reviewer's actual certainty

**Corrective action.** Findings were triaged rather than actioned in sequence. The review prompt was given the specific intended behaviour, since a reviewer without the requirement cannot assess correctness. Critically, an AI review pass was never treated as sufficient — it supplements human review of the areas that matter, and is weakest exactly where SwiftPay's risk concentrates.

---

### 4.9 Documentation

**Objective.** Draft documentation from the actual system, then verify every claim against reality.

**When used.** Continuously, with a dedicated pass before submission.

**Prompt structure.**
```
Role: technical writer
Context: [actual code, config, and compose files — not a description of them]
Task: draft [README / API docs / setup / troubleshooting]
Audience: engineer encountering this repository for the first time
Constraint: every command must come from the actual project files
Constraint: mark anything you inferred rather than read
```

**Developer validation.** Documentation is where AI fabrication is hardest to detect, because the reader has no compiler. Every setup command was executed on a clean clone in an empty directory. Every documented endpoint was called. Every configuration value was checked against the actual file.

The controlling test: **clone fresh, follow the document exactly, and see whether the system runs.** A setup document that requires one undocumented step is a document that fails for every reviewer who uses it — and the reviewer's first experience of the project is it not working.

**Example prompt.**
> Draft the README from the actual files in this repository — the compose file, the Dockerfiles, and the application configs, not from a description. Include: what the system does, architecture, how to run it from a clean clone, how to verify it works, design decisions, and known limitations. Every command must come from the actual project files. Mark anything you inferred rather than read directly.

**Common failure modes.**
- Plausible commands that do not match the project's actual configuration
- Setup steps omitted because they were implicit in the generating environment
- Documented endpoints that do not exist or have different signatures
- Known limitations omitted, since the assistant has no knowledge of what is incomplete
- Confident description of behaviour that was never verified

**Corrective action.** Documentation was generated from actual files rather than from descriptions. Setup instructions were validated by execution on a clean clone. The limitations section was written by the developer, since the assistant has no basis for knowing what does not work.

---

## 5. AI-Assisted SDLC

Five phases, each with defined AI activity, developer responsibility, and an exit gate. Work did not proceed past a gate without explicit developer approval.

> **Note on how these gates actually ran.** The compile → run → test → correct loop described in each phase below is directly evidenced throughout the build. The five gates are not five sequential, separately-committed approvals — the initial commit to the repository contains the full three-service scaffold, with the loop then applied per-service and per-defect from that point onward. Read the phase table as the discipline that governed the whole build, not as a literal one-gate-per-commit sequence. (See Section 11's note on commit history for the supporting detail.)

---

### Phase 1 — Understand

**Input.** Problem statement and requirement document.

| AI activities | Developer responsibility |
|---|---|
| Requirement extraction and classification | Confirm each requirement against the source |
| Ambiguity identification | Resolve every ambiguity by decision |
| Acceptance criteria drafting | Approve criteria as testable |
| Edge-case discovery | Determine which are in scope |

**Gate — Requirements Approved.** No design work began until every surfaced ambiguity had a recorded decision. An ambiguity carried into design becomes a defect in the design.

---

### Phase 2 — Design

| AI activities | Developer responsibility |
|---|---|
| Generate architecture alternatives with trade-offs | **Select** the architecture |
| Component and API design drafts | Evaluate trade-offs against constraints |
| Data model proposals | Approve schema and constraint set |
| Failure scenario enumeration | Define required failure semantics |

**Gate — Design Approved.** Architecture, event topology, schema, concurrency strategy, and API contract fixed and documented, including rejected alternatives and the reason for rejection.

---

### Phase 3 — Implement

| AI activities | Developer responsibility |
|---|---|
| Code generation against approved design | Read every generated line before acceptance |
| Boilerplate and configuration | Compile and execute each unit |
| Refactoring suggestions | Maintain conventions and structural consistency |
| Test drafting | Resolve divergence between design and implementation |

**Gate — Build Successful.** All services compile; the system starts under compose; the primary payment path completes end to end and is observed to do so.

Where implementation revealed a design flaw, work stopped and the design document was amended before continuing. Code and design documents did not diverge silently — divergence between them is a review finding in its own right.

---

### Phase 4 — Validate

| AI activities | Developer responsibility |
|---|---|
| Test scenario enumeration | **Execute** tests and read actual output |
| Code review passes | Triage findings; decide what is a defect |
| Edge case analysis | Inspect runtime behaviour directly |
| Security observations | Verify API behaviour against the contract |
| — | Confirm each requirement ID is satisfied |

**Gate — Validation Passed.** Every requirement ID resolved to satisfied, partially satisfied, or not done. No requirement silently unaddressed.

---

### Phase 5 — Harden

| Area | Activity |
|---|---|
| Security | Input validation, no secrets in source or images, dependency review |
| Error handling | Every failure path produces a defined, observable outcome |
| Logging | Correlation ID traceable across service boundaries |
| Performance | Load target verified by measurement, with the bottleneck identified |
| Configuration | Environment-specific values externalised |
| Dependencies | Versions pinned; transitive tree reviewed |
| Maintainability | Structural consistency; dead code removed |

**Gate — Submission Ready.** Clean clone runs from documented instructions; all deliverable artifacts present; known limitations documented honestly.

---

## 6. Human vs AI Responsibility Matrix

| Activity | AI contribution | Developer ownership |
|---|---|---|
| Requirement analysis | Extraction, classification, ambiguity surfacing | **Final requirement set and scope** |
| Architecture | Alternatives with trade-offs | **Architecture selection and justification** |
| Data model | Schema proposals, constraint suggestions | **Final schema, constraints, and invariants** |
| Concurrency strategy | Options and failure mode analysis | **Strategy selection and verification** |
| Coding | Implementation drafting, boilerplate | **Code ownership and acceptance** |
| Testing | Scenario enumeration, test drafting | **Execution, result interpretation, acceptance** |
| Debugging | Ranked hypotheses | **Root-cause verification by evidence** |
| Code review | Checklist findings | **Triage, severity judgment, resolution** |
| Security | Potential issue identification | **Security validation and final assessment** |
| Performance | Tuning suggestions | **Measurement and acceptance against target** |
| Documentation | Drafting from source files | **Accuracy verification and approval** |
| Deployment configuration | Manifest and pipeline drafting | **Verification that it works from clean state** |
| **Final submission** | Assistance | **Full and undivided responsibility** |

The right-hand column is not advisory. Every item in it required a human action that produced evidence, and no item was satisfied by reading AI output and agreeing with it.

---

## 7. AI Output Verification Protocol

Applied to every AI-generated artifact before integration.

| # | Step | Passes when |
|---|---|---|
| 1 | Review generated output | Every line read and understood; nothing accepted as opaque |
| 2 | Compile / build | Compiles clean, without suppressed warnings |
| 3 | Execute locally | Runs and exercises the intended path |
| 4 | Run automated tests | Tests executed; **actual output read**, not assumed |
| 5 | Validate API behaviour | Endpoints called; responses match the contract |
| 6 | Validate integration behaviour | Cross-service flow observed end to end |
| 7 | Inspect logs and errors | Nothing silently swallowed; failures visible |
| 8 | Check edge cases | Boundary and failure paths exercised deliberately |
| 9 | Review security implications | Input validation, secret handling, injection surfaces |
| 10 | Compare against requirements | Traced to the requirement ID it claims to satisfy |
| 11 | Refactor where required | Consistent with surrounding code and conventions |
| 12 | Accept | All preceding steps evidenced |

### Governing principle

> **No AI-generated implementation was considered complete solely because the generated code appeared correct.**

Appearance of correctness is the one property AI output reliably has. It is therefore worthless as a signal, and the protocol above exists to replace it with observation.

### Verification depth by risk

| Risk tier | Components | Required depth |
|---|---|---|
| **Critical** | Balance mutation, idempotency, transaction boundaries, event handling | Full protocol, plus deliberate failure injection and concurrent execution |
| **Standard** | API endpoints, validation, mapping, configuration | Full protocol |
| **Low** | Documentation, comments, formatting | Steps 1, 10, 12 |

Critical-tier components received failure injection because their defects are invisible under normal operation. A double-spend bug is not observable until two requests race; a broken transaction boundary is not observable until something fails mid-transaction. Neither appears in a passing test suite that does not deliberately provoke them.

---

## 8. Failure and Recovery Cases

> **This section requires actual incidents from the development session. It is deliberately unpopulated.**
>
> Fabricated failure cases are trivially detectable — they lack the specific, awkward detail that real defects have, and a reviewer who probes one will find nothing behind it. An empty, honestly-marked section is stronger than an invented one.

Complete each case using the structure below.

### Case template

**Issue.** What was generated, specifically. Name the class, method, or configuration.

**Detection.** How it surfaced — compiler error, failing test, runtime behaviour, code reading, or a reviewer's question. Include what you were doing when you noticed.

**Root cause.** Why the assistant produced it. Usually one of: missing context in the prompt, an outdated pattern from training data, an invented API, an assumption the assistant made that was never stated, or a requirement the prompt failed to convey.

**Correction.** What changed — the code, and the prompt.

**Lesson.** How the workflow changed so the class of defect does not recur.

---

### Scope note

Cases 1–3 are drawn from the requirements and documentation phase; cases 4–5 are drawn from the implementation and validation phase (`gateway-service`, `ledger-service`). All five are verifiable against the session transcripts and, for cases 4–5, against the corresponding code and commit history.

---

### Case 1 — Requirement inferred from stale context rather than the source document

**Phase.** Requirements understanding.

**Issue.** Asked to help plan the project, the assistant produced a full two-day plan, a risk list, and a set of immediate next steps — all derived from its stored context about the project rather than from the requirement document the developer had just received. The plan was detailed and specific enough to look authoritative, and it was never checked against the document it claimed to plan for.

**Detection.** Caught by the assistant itself, but only after the plan was already written, and only as a caveat appended to it rather than as a question asked first. The developer had not yet supplied the document.

**Root cause.** Sufficient adjacent context existed to generate a plausible answer, so the assistant generated one. Having enough information to produce output is not the same as having the right information, and the assistant does not reliably distinguish the two — plausibility is available from partial context, correctness is not.

**Correction.** The plan was treated as provisional pending the actual document. In the subsequent phase the assistant checked directly for the attached file rather than assuming it was present, found nothing attached, and said so before generating — the correct ordering, applied one step late.

**Lesson.** Verify that the source input is actually present before generating anything that depends on it. This was written into the playbook as a standing rule in §4.1 — supply the document verbatim, never a summary — and into §4.9, where documentation must be drafted from actual files rather than descriptions of them. The general form of the rule: an assistant asked to work from a document it does not have will produce something anyway.

---

### Case 2 — Ambiguous requirement resolved against the wrong candidate set

**Phase.** Requirements understanding.

**Issue.** The developer relayed a verbal request for "the prompt and workbook." The assistant enumerated three possible meanings of "workbook," wrote guidance for distinguishing them, and asked which was intended. The actual term was **playbook** — not in the candidate set at all. The work of enumerating and explaining the three options was wasted.

**Detection.** The developer corrected it in a single word.

**Root cause.** The requirement arrived through a lossy channel — a phone call, relayed from memory, with no written source. The assistant treated the relayed term as reliable input and reasoned carefully from a wrong premise. Careful reasoning from an unverified input produces confident, well-structured, wrong output, and its quality makes it harder to spot rather than easier.

**Correction.** Scope was re-established against the correct artifact. When the developer then supplied the actual written specification, the work proceeded from that document rather than from the relayed description of it.

**Lesson.** When a requirement arrives verbally or second-hand, obtain the written source before generating — not after. Asking a clarifying question is necessary but not sufficient: the question must be asked against the source, not against the relay. This reinforces the §4.1 rule that the full document is supplied verbatim, and the ambiguity list in that section exists precisely to catch this class of error at the start rather than mid-deliverable.

---

### Case 3 — Unstated assumption embedded in a generated artifact

**Phase.** Design and documentation.

**Issue.** Generating the project operating instructions, the assistant selected Spring Boot over Quarkus and fixed a priority order for what to cut under time pressure. The requirement specification permitted either framework. Neither choice was requested by the developer; both were embedded in the artifact as though settled.

**Detection.** Flagged by the assistant when delivering the artifact, and confirmed as a decision the developer needed to own rather than inherit.

**Root cause.** Generating a complete artifact requires resolving every open variable in it. Where a variable is unresolved, the assistant fills it rather than leaving a gap, because a gap makes the output incomplete and completeness is what it optimises for. The filled value is then indistinguishable in the output from a value that was actually specified.

**Correction.** Both choices were surfaced explicitly at delivery, with the instruction to change the priority ordering if the assessment's judging weights differed.

**Lesson.** Require assumptions as a separate, mandatory output field rather than relying on them being flagged in prose. This became a standing element of the implementation prompt template in §4.4 and §12 — *"List any assumptions you made"* — because an assumption stated in a separate list is reviewable, while the same assumption embedded in an artifact is invisible.

---

### Case 4 — Self-invocation defeated a transaction boundary silently

**Phase.** Implementation — `gateway-service` payment persistence.

**Issue.** Generating `PaymentService`, the assistant produced a single class with a public `initiatePayment` method that called a `protected`, `@Transactional`-annotated method (`processNewPayment`) on `this`, within the same class. Spring's default proxy-based AOP cannot intercept a call made from inside the object it wraps — the `@Transactional` annotation would have taken no effect at all. The code compiled cleanly and read as correct. The defect had no error message and no failing behaviour under normal operation; it would only ever surface as a missing rollback under a deliberate mid-transaction failure test.

**Detection.** By code reading, at generation time, before the file was ever run — not by a test failure. This is a known Spring proxy limitation, and it was checked for specifically because the method in question governed money movement.

**Root cause.** The assistant's default pattern for "a service exposing one public method" is a single class. That pattern silently breaks Spring's transactional guarantee whenever the annotated method is reached via an internal call rather than through the bean's external proxy, and the assistant had no way to flag this on its own — the generated code was syntactically and stylistically normal.

**Correction.** The method was split across two beans: `PaymentService` (thin, non-transactional, owns the idempotency check) and a new `PaymentPersistenceService` (owns the single `@Transactional` method), so the call now crosses a real Spring proxy boundary. The identical pattern was then checked for and caught proactively — before it was ever written — in `analytics-worker`'s equivalent `PaymentIngestionService`, on the second occasion the same one-class shape was about to be generated.

**Lesson.** Every `@Transactional` method is now checked for internal-call risk before acceptance, not after. `ledger-service`'s `LedgerTransactionService` and its Kafka listener were deliberately built as separate beans from the outset specifically to avoid re-creating this defect a third time.

---

### Case 5 — A test suite that compiled, and never ran

**Phase.** Validation — `ledger-service` integration tests.

**Issue.** A new test class was named `LedgerTransactionServiceIT`, following the common "integration test" naming convention. `mvn test` returned `BUILD SUCCESS` with no errors, but the Surefire test-execution section produced no output at all for that class — no "Running com.swiftpay.ledger.service..." line, no test count, nothing. The class had compiled and was then never executed by anything.

**Detection.** Only visible by comparing two build outputs side by side in the same session: the equivalent `mvn test` run against `gateway-service`, moments earlier, had printed "Tests run: 4, Failures: 0" for its test class; the `ledger-service` run's Surefire section was simply silent where that line should have been. A clean `BUILD SUCCESS` with no test-count output reads as "nothing needed testing," not as "the tests never ran" — that is exactly what makes this failure mode dangerous.

**Root cause.** Maven's Surefire plugin, which `mvn test` invokes, only selects classes matching its default patterns — `*Test.java`, `Test*.java`, `*Tests.java`. The `*IT` suffix is a Failsafe-plugin convention bound to `mvn verify`, and no Failsafe plugin was configured in the project. The file was therefore syntactically valid, fully compiled, and invisible to the one command being relied on to prove it worked.

**Correction.** The class was renamed `LedgerTransactionServiceTest`. Re-run, it correctly reported "Tests run: 4, Failures: 0, Errors: 0" — including the concurrent double-spend test, the single highest-value test in the project, which had never actually executed until this rename.

**Lesson.** A green `BUILD SUCCESS` is not evidence a test ran; the test-count line in the Surefire output is. Every subsequent test class in the project was checked, by name, for the pattern Surefire actually selects, before its first run was trusted.

---

**Additional real cases exist beyond these two** — a Kafka Kubernetes deployment that crash-looped from a self-referential Service DNS lookup (diagnosed from raw broker logs, not guessed), a Kubernetes manifest referencing an image name that didn't match what `docker compose build` actually produced, and a Testcontainers/Docker Desktop version incompatibility resolved by searching for the specific error signature rather than guessing a fix. These can be written up in the same format if a stronger Section 8 is worth the space — say so and they'll be added.

---

**Where to find the implementation cases.** In the build sessions, look for: a compile error in generated code; a dependency or method that did not exist; a configuration value wrong for your environment; a test that passed when it should not have; any point where you re-prompted because the first answer was wrong. The re-prompts are usually the best cases — they show the correction loop working, which is what the reviewer is actually assessing.

Cases 1–3 above establish that the loop existed. Two implementation-phase cases would demonstrate it operating on code, which is stronger. If the build sessions genuinely contain none, submit with three and say so — a documented boundary is credible; an invented fourth case is not.

---

## 9. Prompt Engineering Standards

### The framework

| Component | Purpose | Consequence of omission |
|---|---|---|
| **Context** | System, stack, existing code, constraints already fixed | Output that ignores the codebase it must live in |
| **Role** | The expertise frame to adopt | Generic output at the wrong level of depth |
| **Objective** | The single specific outcome wanted | Multiple half-addressed goals |
| **Constraints** | Fixed technology, forbidden approaches, conventions | Substituted technology; broken conventions |
| **Input** | Actual code, actual logs, actual documents | Output based on the assistant's guess at your system |
| **Expected output** | Form, structure, completeness | Unusable format requiring manual restructuring |
| **Validation criteria** | How the output will be judged | No shared definition of done; silent assumptions |

The last component is the one most often omitted and the most valuable. Stating how output will be judged causes the assistant to surface its uncertainty rather than mask it.

### Transformation example

**Weak prompt**

> Create an API for this.

Fails on every component. No context, no constraints, no specification of behaviour, no output expectation. The assistant must invent the stack, the data model, the error semantics, and the conventions — and it will, confidently, producing something that looks finished and matches nothing.

**Enterprise prompt**

> **Context:** SwiftPay transaction gateway, Java 21, Spring Boot 3, PostgreSQL, Redis for idempotency, Kafka producer already configured in `config/KafkaConfig.java`. Existing package structure under `com.swiftpay.gateway` — follow it.
>
> **Role:** Implementing engineer on this existing codebase.
>
> **Objective:** Implement the payment initiation endpoint. Satisfies FR-01, FR-04.
>
> **Constraints:** Use the existing Kafka and Redis configuration — do not create new ones. No new dependencies without telling me first. Follow the existing package and naming conventions. Do not use floating-point types for monetary values.
>
> **Input:** [request/response schema, `PaymentInitiated` event schema, relevant existing classes]
>
> **Expected behaviour:** Validate the request. Enforce idempotency by header key — a repeated key returns the original response without re-processing, and two concurrent requests with the same key must not both proceed. Check the balance precondition. Emit `PaymentInitiated` to Kafka. Return 202 with the transaction ID. Insufficient funds returns 422 with the standard error shape. Validation failure returns 400.
>
> **Expected output:** Controller, service, and DTOs as complete compilable code. Separately: every assumption you made, and anything you were uncertain about.
>
> **Validation criteria:** I will compile this, call it with a duplicate idempotency key, call it with concurrent duplicate keys, call it with insufficient balance, and check that exactly one event reaches the topic in each case.

The difference is not length. It is that the second prompt removes every decision the assistant would otherwise make silently, and tells it in advance how the output will be tested.

### Standards applied

1. State constraints before generation, not as corrections afterward
2. Supply actual code and actual logs, never descriptions of them
3. Define the expected output format explicitly
4. State validation criteria in the prompt
5. Require assumptions to be listed as a separate output
6. For design work, prohibit recommendation and require alternatives
7. For debugging, require hypotheses and prohibit fixes in the first response
8. One objective per prompt

---

## 10. Enterprise AI Guardrails

| # | Guardrail | Rationale |
|---|---|---|
| 1 | No blind acceptance of generated code | Fluency is uncorrelated with correctness |
| 2 | No secrets, API keys, or credentials in prompts | Prompt content leaves the local environment |
| 3 | No production or real customer data in prompts | Same exposure; compounded by regulatory exposure |
| 4 | Validate every dependency introduced | Hallucinated, abandoned, and vulnerable packages are all plausible-looking |
| 5 | Verify generated API calls against real documentation | Invented methods on real classes are syntactically valid |
| 6 | Verify security assumptions independently | AI security reasoning is pattern-based, not contextual |
| 7 | Review third-party library choices | Popularity in training data is not current suitability |
| 8 | Execute all tests; read actual output | A test suite's failure mode is false assurance |
| 9 | Review all generated SQL | Injection surfaces and unindexed scans are invisible until load |
| 10 | Review all authentication and authorization logic manually | Highest consequence, lowest AI reliability |
| 11 | Validate error handling paths | Swallowed exceptions convert failures into silent data corruption |
| 12 | Maintain human approval gates | Removing the gate removes the control |

**Guardrail #2 in practice.** This guardrail is written as preventative; the evidence for it is corrective. A Personal Access Token was pasted into a real session once during this project — the guardrail did not stop that, it ensured the incident was caught immediately and remediated (the token was flagged, the developer instructed to revoke it, and the local git configuration stripped of it — see Section 13). Stated plainly rather than implied: this guardrail functioned as a detection-and-response control, not a prevention control, on its one real test.

### Practices specific to financial systems

| Practice | Why it matters here | Status in this project |
|---|---|---|
| Exact numeric types for all monetary values | Floating-point money is a defect class that testing rarely catches | Applied — `NUMERIC(19,4)` throughout |
| Every balance mutation inside an explicit transaction boundary | Partial movement corrupts the ledger permanently | Applied and verified — see Section 8, Case 4 |
| Idempotency logic tested against concurrency branches | Sequential testing cannot detect the race it must prevent | **Partial** — unit-tested against a mocked Redis (`IdempotencyServiceTest`); a live concurrent-duplicate submission against a real Redis instance was not performed. See Section 13. |
| Every failure path produces an observable outcome | A silently dropped payment is worse than a rejected one | Applied for the paths exercised (insufficient funds, validation); the database-outage retry path was not exercised end to end — see Section 13 |
| Balance sufficiency enforced under a database-issued row lock, invoked from application code | Application-layer invariants checked without a lock fail under concurrent access | Applied and verified — `SELECT ... FOR UPDATE` acquired via `LedgerTransactionService`, proven under genuine concurrent execution (Section 8) |

---

## 11. Evidence and Audit Trail

> **Mark each item honestly. Do not list evidence you cannot produce on request.** A reviewer who asks for one item on this list and receives nothing will discount the entire document.

| Evidence type | Available | Location |
|---|---|---|
| Prompt history from development sessions | Yes | Full session transcript (exportable) |
| Iterative prompt refinement examples | Yes | Same transcript — e.g. diagnosing and correcting a Testcontainers/Docker Desktop version incompatibility across several attempts |
| Commit history showing incremental development | Partial — see note below | `github.com/San14deep08/swiftpayapp`, commit history |
| Test execution output | Yes | Session transcript (pasted verbatim); reproducible by running `mvn test` |
| Build and CI pipeline results | Yes | `github.com/San14deep08/swiftpayapp/actions` — confirmed passing run |
| Architecture diagrams | Partial | ASCII diagram in `README.md`; no separate `docs/` folder |
| Debugging conversation transcripts | Yes | Session transcript |
| Before/after correction examples | Yes | Session transcript; also visible as in-code comments left in place at the correction site (e.g. `k8s/12-kafka.yaml`, `PaymentPersistenceService.java`) |
| Load test results and packet capture | Yes | `github.com/San14deep08/swiftpayapp/releases/tag/v1.0-loadtest-1m`; results table in `README.md` |
| Requirement traceability matrix | Yes | See table below — added post-audit |
| Validation checklist, completed | Yes | Section 13 of this document |

### Requirement traceability matrix

Deliberately lightweight — five correctness rules, not an enterprise-scale requirement register, because that is the actual size of the non-negotiable set defined in the System Prompt.

| Req ID | Requirement | Implementation | Test | Status |
|---|---|---|---|---|
| COR-1 | Idempotency — a duplicate submission produces exactly one ledger movement | `IdempotencyService` (gateway-service) | `IdempotencyServiceTest` | Partial — verified against mocked Redis concurrency branches; live concurrent-duplicate submission against a real Redis instance not performed |
| COR-2 | Debit and credit are atomic; no partial movement survives a failure | `LedgerTransactionService` (ledger-service) | `LedgerTransactionServiceTest` | Verified — insufficient-funds path proven to leave both balances untouched |
| COR-3 | Insufficient funds fails cleanly, no write, no silent drop | `PaymentPersistenceService`, `LedgerTransactionService` | `LedgerTransactionServiceTest` | Verified — live call against a zero-balance account and automated test both confirm |
| COR-4 | Kafka consumer survives a database outage without message loss | `KafkaConsumerConfig`, `NonRetryableExceptions` | `NonRetryableExceptionsTest` | Partial — retry-vs-DLQ exception classification is unit-tested in isolation; end-to-end behaviour under a real outage was not verified (two live attempts failed on tooling, not on the code — see Section 13) |
| COR-5 | Two concurrent transfers from one account cannot both succeed against the same balance | `LedgerTransactionService` (row locking) | `LedgerTransactionServiceTest` (concurrent test) | Verified — proven under genuine concurrent execution, race resolved a different way on two separate runs, invariant held both times. Strongest evidence in the project. |

### Note on commit history

Commit history is the evidence reviewers trust most, because it is the hardest to construct after the fact. Here it is a mixed picture, stated plainly rather than smoothed over: the initial commit contains the full three-service scaffold in one push, not built up service-by-service; the commits that follow it — adding tests, fixing the CI workflow, adding load-test tooling, adding Kubernetes manifests, documenting the load-test and Kubernetes results — are genuinely incremental, each with a message describing what changed and why. A reviewer comparing this document's phase-gate description (Section 5) against that history should read the gates as describing the compile-execute-test-correct loop that governed everything from the initial scaffold onward, not as five sequential commits.

---

## 12. Reusable Prompt Templates

Ready to use on the next project. Replace bracketed values.

### Requirements extraction
```
Read [DOCUMENT] in full.
Extract every requirement as a numbered, individually testable item.
Classify: FR- functional, NFR- non-functional, CON- constraint.
Mark each Explicit (stated in the document) or Inferred (you concluded it).
Separately: every ambiguity as a direct question with a recommended default.
Separately: every artifact the document requires as a deliverable.
Do not resolve ambiguities yourself.
```

### Design alternatives
```
Context: [SYSTEM, STACK, CONSTRAINTS, TARGETS]
Propose [N] distinct approaches to [DECISION].
Per approach: mechanism, failure behaviour, performance implication,
operational complexity, what it forecloses.
Do not recommend one. Present a comparison table.
List what I need to decide.
```

### Schema design
```
Context: [ENTITIES, ACCESS PATTERNS, THROUGHPUT]
Provide logical model, then physical DDL.
Per constraint: state the invariant it protects.
Per index: state the query it serves.
[DOMAIN RULES — e.g. money uses exact numeric types]
Provide forward-only migrations.
```

### Implementation
```
Context: [DESIGN, SCHEMA, EXISTING CONVENTIONS]
Implement [COMPONENT]. Satisfies [REQUIREMENT IDS].
Constraints: follow existing structure and naming;
no new dependencies without stating them; no invented APIs.
Error handling: [EXPECTED BEHAVIOUR]
Output: complete compilable code, plus a separate list of assumptions.
Validation: I will [HOW YOU WILL TEST IT].
```

### Test enumeration
```
Before writing any code, enumerate test scenarios for [COMPONENT].
Cover: happy path, boundary, negative, concurrency, failure injection.
Per scenario: requirement ID and expected observable outcome.
I will approve the list before you implement.
```

### Debugging
```
Problem: [OBSERVED BEHAVIOUR]
Logs: [VERBATIM OUTPUT]
Code: [RELEVANT PATH]
Expected: [CORRECT BEHAVIOUR]
Ruled out: [WHAT, AND HOW YOU ELIMINATED IT]
Give ranked hypotheses. Per hypothesis, the observation that
would confirm or eliminate it. Do not propose a fix yet.
```

### Code review
```
Review [CODE] against: correctness, SOLID, security, performance,
maintainability, readability, error handling, duplication, API design.
Intended behaviour: [DESCRIPTION]
Per finding: category, severity, location, why it matters, suggested change.
Do not rewrite. State explicitly what you are uncertain about.
```

### Documentation
```
Draft [DOCUMENT] from the actual files in this repository,
not from a description of them.
Audience: an engineer seeing this repo for the first time.
Every command must come from the actual project files.
Mark anything you inferred rather than read directly.
```

---

## 13. Final Review Checklist

Completed against the actual build transcript rather than left as a template. Every unchecked or partial item names the specific gap rather than being silently rounded up — per this document's own governing principle in Section 7, an unchecked box with a reason is worth more than a checked box without one.

### Engineering
- [x] All services compile without warnings — `mvn test` run clean across all three modules (`gateway-service`, `ledger-service`, `analytics-worker`)
- [x] System starts from a clean clone with the documented command, no undocumented steps — verified repeatedly via `docker compose up --build` against fresh extractions
- [x] Primary payment path verified end to end by observation — live HTTP calls, `PENDING` → `COMPLETED` confirmed via the transaction-history endpoint
- [ ] **Partial** — Idempotency verified under genuine concurrency — the claim/replay/duplicate-in-flight logic is unit-tested against a mocked Redis (`IdempotencyServiceTest`, 4/4 passing); two real simultaneous duplicate submissions against a live Redis were never executed
- [ ] **Partial** — Atomicity verified by forced mid-transaction failure — the insufficient-funds path is proven atomic (`LedgerTransactionServiceTest`: a failed transfer leaves both balances untouched); a deliberate crash injected between the debit and credit steps of a transfer that would otherwise succeed was never tested
- [x] Insufficient funds verified at the exact boundary — both a live call against a `$0`-balance account and an automated test
- [ ] **Not done** — Consumer resilience verified by deliberate database outage — two live attempts to simulate this failed on tooling (`docker compose`'s dependency auto-restart, then a pause/unpause conflict) rather than proving the behaviour; only the retry/DLQ exception *classification* was unit-tested in isolation. This is the single largest unresolved item in the project and is stated as such in the README rather than implied to be covered.
- [x] Concurrent transfers from one account verified not to double-spend — proven twice, with the race producing a different winner each time, against the same passing invariant (see Section 8, and the strongest evidence in the repository)
- [x] All tests executed; output read, not assumed — this is the practice that surfaced Case 5 in Section 8
- [ ] **Partial** — Every requirement ID resolved to satisfied / partial / not done — every requirement from the source specification was addressed and its status documented in prose (`README.md`'s verification log), but no formal requirement-ID traceability matrix exists to check items off against (see Section 11)

### AI governance
- [x] No generated code integrated without compilation and execution
- [ ] **Not fully clean — disclosed rather than hidden.** A GitHub Personal Access Token was pasted into a development session in plaintext during a git-push troubleshooting step. It was flagged immediately, the developer was instructed to revoke it, and the local git remote was reset to strip the token from stored configuration. No production data was ever involved. This is recorded here rather than omitted because a governance section that hides a real incident is worth less than one that shows the incident being caught.
- [x] All dependencies verified to exist and be current — e.g. a Testcontainers version bump and two GitHub Actions version bumps were each confirmed against real release information before being applied, not guessed
- [x] All generated SQL reviewed — `db/init.sql` schema and constraints
- [x] All generated API calls verified against documentation — largely by compile-and-run rather than doc lookup, consistent with this document's own stated practice (Section 2); one Spring Kafka API mismatch (`Class<? extends Exception>` vs. the generated `Class<? extends Throwable>`) was caught by the compiler and corrected, not missed
- [x] Failure cases in Section 8 are real, or the section is removed — five real cases, none fabricated
- [x] Evidence in Section 11 is producible on request — including the two items honestly marked as not produced

### Documentation
- [x] Setup instructions executed on a clean clone in an empty directory
- [ ] **Partial** — Every documented endpoint called and verified — `POST /v1/payments` and `GET /v1/users/{id}/transactions` were called live repeatedly; `analytics-worker`'s `GET /v1/analytics/volume` was verified only at the service/repository layer via automated test, never called live over HTTP
- [x] Known limitations stated honestly — the README's verification log and the Kubernetes README's instability writeup are both direct examples
- [x] Design documents match the delivered code — the README's design-decisions section was updated repeatedly as real findings changed the picture (e.g. the load-test bottleneck discussion, the Kubernetes verification log)
- [x] No fabricated metrics anywhere in this document — every number (249.87 TPS, 0% failure rate, p95 14.06ms, 999,490 of 1,000,000 transactions) is read directly from real tool output, not estimated

---

## 14. Conclusion

SwiftPay was delivered using AI assistance under a human-in-the-loop engineering workflow. The assistant accelerated scaffolding, configuration, test enumeration, and documentation drafting. It did not select the architecture, design the schema, choose the concurrency strategy, decide the failure semantics, or determine when the system was correct. Those decisions were made by the developer, and the developer carries full responsibility for the submitted system.

The workflow rests on one principle: **generated output is a proposal, and proposals require evidence.** Every verification step in this document exists because appearance of correctness is the one property AI output reliably has, which makes it useless as a signal and requires replacing it with observation.

The result is a development approach that is materially faster than unaided work and that produces a system whose correctness is established the same way it always was — by running it, breaking it deliberately, and checking what it actually does.

---

### Document status

| Section | Status |
|---|---|
| 1–9, 12 | Complete |
| 8 — Failure cases | Complete — 5 real cases; cases 4–5 drawn from implementation and validation |
| 10 — Guardrails | Complete — corrected post-audit: guardrail #2 reframed as a detection-and-response control rather than a preventative one, based on its one real test; financial-practices table corrected to match Section 13's verification status exactly |
| 11 — Evidence | Complete — including a requirement traceability matrix, added post-audit |
| 13 — Final Review Checklist | Complete against real evidence |
| Executive summary velocity claims | Left qualitative, as instructed — no measured percentage exists to report |

**Post-audit remediation applied:** this document underwent an independent enterprise-readiness audit (`swiftpay-enterprise-readiness-audit.md`). The audit's six priority findings — an idempotency claim in Section 10 that contradicted Section 13, an unverified non-negotiable correctness rule stated without a visible status flag, a missing requirement traceability matrix, a phase-gate narrative not matched by commit history, a missing security clause in the companion System Prompt, and an imprecise database-constraint claim — have all been applied. The System Prompt's corresponding fix is recorded there as two labeled addenda (Sections 8–9) rather than a silent edit to its verbatim historical text.
