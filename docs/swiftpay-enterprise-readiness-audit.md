# SwiftPay AI Documentation — Enterprise Readiness Audit

**Artifacts under review:**
- System Prompt → `ai-prompt-operating-instructions.md`
- Prompt Playbook + SDLC Playbook → combined into one document, `ai-engineering-playbook.md` (Section 4 = Prompt Playbook, Section 5 = SDLC Playbook, remaining sections = supporting evidence and governance)

**Note on structure before the audit begins:** the request assumes three separate artifacts. Two exist. This is not itself a defect — the combined document covers every role the third would — but a reviewer expecting three files will notice the count is wrong before reading a word of content. Named here so it isn't discovered as a surprise.

**Evidence base:** this audit draws only on the two documents above and the actual build session that produced them (the live commands run, their real output, and the resulting commits). Nothing below is invented. Anything the documents claim that this audit cannot verify against that session is marked `[EVIDENCE REQUIRED — DO NOT FABRICATE]`.

---

## 1. Executive Assessment

**Classification: Enterprise Ready with Remediation.**

What earns that rather than a lower tier: Section 8's failure cases are real, specific, and named down to the class and method (a Spring `@Transactional` self-invocation defect, a Surefire/Failsafe naming mismatch that let a test suite silently never run) — not the generic "AI made a mistake and I fixed it" language that a red-team review is designed to catch. Section 13's checklist discloses genuine unresolved gaps, including a Personal Access Token pasted into a session in plaintext, rather than rounding every box up to checked. That combination — specific defects, disclosed gaps — is the strongest evidence this describes a real process rather than a document written to sound like one.

What prevents a higher tier: one of the System Prompt's five items explicitly marked **non-negotiable** — consumer resilience under a database outage — was never actually verified end to end, and the Playbook's own Section 10 states a related claim ("idempotency verified under genuine concurrency") as an applied practice that Section 13, in the same document, marks partial. An internal contradiction on a claim this central is the single largest finding in this audit, detailed in Sections 8 and 10 below.

---

## 2. System Prompt Audit

| Element | Status | Basis |
|---|---|---|
| Role | PASS | "engineering collaborator on SwiftPay" — specific, scoped, not generic |
| Context | PASS | Service table, mandatory stack, repo layout all stated before any task begins |
| Objectives | PASS | Priority-ordered list (§6) with an explicit statement that lower items wait for higher ones |
| Constraints | PASS | Stack is named as non-substitutable; correctness rules are named as non-negotiable |
| Coding standards — maintainability, readability | PARTIAL | "match the existing code" (§5) is a consistency instruction, not a maintainability standard. No SOLID reference. |
| Coding standards — error handling | PASS | Covered structurally via the five correctness rules, which are error-handling requirements in substance |
| Coding standards — security | **FAIL** | No clause addressing secrets, credentials, or sensitive data in prompts or output. This is not theoretical: a GitHub Personal Access Token was pasted into the session in plaintext during the actual build (disclosed in the Playbook's Section 13). A prompt with an explicit secrets clause does not guarantee this doesn't happen, but its absence here is a documented, not hypothetical, gap. |
| Coding standards — performance | PARTIAL | Addressed only via the load-test target (§4), not as a standing implementation standard |
| Coding standards — testing | PASS | §6 places Testcontainers-based tests in the priority order; §7 requires observed, not assumed, correctness |
| Coding standards — logging | PASS | Correlation-ID requirement stated explicitly (§4) |
| Coding standards — dependency management | PASS | "No dependencies... you have not confirmed exist" (§5) |
| Output requirements | PASS | Definition of Done (§7) is explicit and falsifiable |
| Validation / uncertainty disclosure | PASS | "Verify before you claim" (§5) directly requires the assistant to state "untested" rather than imply completion |
| Safety — secrets exposure | **FAIL** | Same gap as above; no instruction to the assistant to flag or refuse secret material in a prompt or a generated file |
| Safety — unverified dependencies | PASS | Explicit ("Don't invent," §5) |
| Safety — unsafe implementation recommendations | PARTIAL | Covered narrowly (no dependency invention, no API invention) but not as a general safety clause |
| Human oversight — architecture | PASS | "Ask before rewriting" plus the priority-order structure keeps sequencing decisions with the developer |
| Human oversight — final acceptance / submission | PARTIAL | Implied by §7's Definition of Done, never stated as an explicit ownership clause ("the developer owns final acceptance and submission") |

**Net:** strong on scope, sequencing, and verification discipline; the one clear gap is security/secrets handling, and it is not a theoretical gap — it manifested once during the actual build.

---

## 3. Prompt Playbook Audit (Playbook §4, nine categories)

The requested structure is Context → Role → Objective → Constraints → Input → Expected Output → Validation. The nine categories (§4.1–4.9) carry all seven elements, though not always under those exact labels — "Prompt structure" plus "Expected output" plus "Developer validation" functionally covers Input/Expected Output/Validation.

| Category | Structural completeness | Note |
|---|---|---|
| 4.1 Requirements Understanding | PASS | Includes an explicit instruction not to resolve ambiguity — a control most playbooks omit |
| 4.2 Architecture/Design *(referenced by §4.3's schema section; not separately reviewed here — evidence base did not include its full text)* | `[EVIDENCE REQUIRED — DO NOT FABRICATE]` | Not verified against source in this audit pass |
| 4.3 Schema Design | PASS | Failure-mode list is specific (floating-point money, unindexed speculative indexes) rather than generic |
| 4.4 Backend Development | PASS | Names the exact defect class that recurred in the real build (`@Transactional` self-invocation) as a *documented common failure mode*, before Section 8 independently confirms it happened |
| 4.5 Frontend Development | PASS (N/A, correctly marked) | Honestly marked not-applicable with a stated reason, rather than populated with invented content |
| 4.6 Testing | PASS | Explicitly requires a test to be shown failing against broken code before being trusted — the single strongest testing control in the document |
| 4.7 Debugging | PASS | Explicitly separates hypothesis generation from fix proposal — a real, checkable discipline, not a slogan |
| 4.8 Code Review | PASS | Names AI review's specific bias (style flagged at defect-severity; concurrency issues under-caught) rather than asserting review happened |
| 4.9 Documentation | PASS | "Clone fresh, follow the document exactly" is a falsifiable test, not an assertion |

**Consistency with System Prompt:** consistent. No category in §4 contradicts a constraint stated in the System Prompt.

---

## 4. SDLC Playbook Audit (Playbook §5–§7)

| Phase | AI role stated | Developer role stated | Gate stated | Real-build match |
|---|---|---|---|---|
| Understand | Yes | Yes | Yes ("Requirements Approved") | `[EVIDENCE REQUIRED — DO NOT FABRICATE]` — no artifact in evidence base shows a discrete requirements-approval step separate from design |
| Design | Yes | Yes | Yes ("Design Approved") | Same — no separate design-approval artifact in evidence base |
| Implement | Yes | Yes | Yes ("Build Successful": compiles, starts under compose, primary path observed) | **Matches real evidence** — this is exactly what the session shows happening, repeatedly, service by service |
| Validate | Yes | Yes | Yes ("Validation Passed") | Partially matches — validation happened continuously, but "every requirement ID resolved" did not happen as a discrete, itemized step (Playbook §11 already admits no traceability matrix exists) |
| Harden | Yes | Yes | Yes ("Submission Ready") | Matches — CI, load test, and documentation hardening are all directly evidenced |

**Finding.** The five-gate structure is credible as a description of a compile→run→test→correct discipline, which real evidence strongly supports. It is less credible as a description of five sequential, discretely-approved stages, because the actual repository's initial commit contains the full three-service scaffold in one push — not a Design-approved artifact followed by a separately-gated Implementation. The Playbook's own §11 already discloses this ("the initial commit contains the full three-service scaffold... not built up service-by-service"). That disclosure is good practice. It does not fully resolve the tension: a reviewer reading §5 before reaching §11's caveat will form an impression the commit history doesn't support. **Recommend moving the §11 caveat, or a shortened version of it, into §5 directly**, so the qualification sits next to the claim rather than four sections later.

---

## 5. Human-in-the-Loop Audit

Checked for the pattern: AI proposes → Engineer evaluates → validates → accepts/rejects → integrates.

**Present throughout**, and the strongest evidence for it is Section 8, Case 3: the assistant embedded an unstated framework choice (Spring Boot over Quarkus) inside a generated artifact, and this was caught and surfaced as a decision requiring developer ownership — not silently accepted. That is the loop operating on a real instance, not a description of the loop in the abstract.

**Language check — did any AI output get treated as authoritative without evaluation?** No instance found in the evidence base. The one place this risk was highest — accepting an "insufficient funds → 202" HTTP status design generated during implementation — was itself caught and revised (to 422) specifically because the developer evaluated it against what the status code actually communicates, not because it was flagged as broken by any tool.

**Gap.** The loop is well-evidenced for *code* decisions. It is thinner for *documentation* decisions — the Playbook itself was drafted by AI, and while its content was reviewed and corrected (this very audit is an instance of that review), there is no discrete evidence trail analogous to Section 8's code cases for the documentation-drafting loop specifically. `[EVIDENCE REQUIRED — DO NOT FABRICATE]` if a reviewer asks for one.

---

## 6. Verification & Testing Audit

The Playbook's Section 7 avoids the vague "developer reviewed the output" language the audit brief specifically warns against — it names twelve discrete steps (review, compile, execute, test, validate API behaviour, validate integration behaviour, inspect logs, check edge cases, security review, compare to requirements, refactor, accept). That is a real structural strength.

**Where the evidence base independently confirms these steps happened:**
- Build verification — yes, `mvn test` run to completion across all three services, real output read
- Integration testing — yes, Testcontainers-based Postgres integration tests, including a genuine concurrent-execution test that produced a different race winner on two separate runs (the single strongest piece of evidence in the entire project — an invariant proven under real, non-deterministic concurrency, not asserted)
- API testing — yes, live HTTP calls against the running gateway and ledger services
- Regression / runtime verification post-deploy — yes, GitHub Actions CI run confirmed green on a clean checkout

**Where the evidence base does NOT confirm the step, despite related claims elsewhere in the documents:**
- **Idempotency verified under genuine concurrency** — Playbook §10 lists this as an applied financial-system practice. Playbook §13 marks it "Partial," honestly noting the concurrency behaviour was verified only against a mocked Redis, not two live simultaneous requests. **These two statements are in direct tension inside the same document.**
- **Consumer resilience verified by deliberate database outage** — this is System Prompt correctness rule #4, marked non-negotiable. Playbook §13 marks it "Not done": two live attempts failed on tooling before any actual outage behaviour was observed, and only the retry/DLQ *classification logic* was unit-tested in isolation from a real broker.

Both gaps are already disclosed in Section 13 — crediting the document for honesty does not remove the finding that Section 10 overstates status relative to Section 13's own admission in the same artifact.

---

## 7. Security & Governance Audit

| Control | Documented | Evidenced in practice |
|---|---|---|
| No secrets in prompts | Playbook §10, guardrail #2 | **Violated once, then caught and remediated** — a PAT was pasted into the session; flagged immediately; developer instructed to revoke it; local git remote config stripped of the token. Disclosed in Playbook §13. |
| No production data in prompts | Playbook §10, guardrail #3 | No evidence of violation; also no positive evidence system handled real customer data at all, so this control was never actually tested |
| Dependency validation | Playbook §10, guardrail #4 | Evidenced — a Testcontainers version bump and two GitHub Actions action-version bumps were each confirmed against real release information before being applied |
| API call verification against real docs | Playbook §10, guardrail #5 | Partially evidenced by a compiler-caught counterexample: a Spring Kafka API call (`addNotRetryableExceptions`) was generated with the wrong generic type and caught by the compiler, not by prior documentation lookup. This is consistent with the document's own stated practice in §2 ("verification happens by execution, not by reading") — worth noting explicitly rather than leaving §10's language to imply doc-lookup was the primary method. |
| SQL review | Playbook §10, guardrail #9 | Evidenced — schema constraints inspected directly in the generated DDL |
| Auth/authz manual review | Playbook §10, guardrail #10 | `[EVIDENCE REQUIRED — DO NOT FABRICATE]` — SwiftPay as built has no authentication layer; this guardrail was never exercised because there was nothing for it to apply to. Worth stating plainly rather than silently omitting. |
| Human approval gates maintained | Playbook §10, guardrail #12 | Evidenced throughout — see Section 5 of this audit |

**Governance finding worth stating plainly, not softened:** the PAT-exposure guardrail existed on paper and did not prevent the incident — it only ensured the incident was caught and corrected afterward. That is a real difference between a *preventative* and a *corrective* control, and the Playbook's current language ("No secrets... in prompts") reads as preventative. Recommend the guardrail table distinguish the two, since claiming prevention where only correction was demonstrated is exactly the kind of gap a skeptical reviewer is instructed to find.

---

## 8. Consistency Matrix

| Rule / Principle | System Prompt | Prompt Playbook (§4) | SDLC Playbook (§5–7) | Status |
|---|---|---|---|---|
| Developer owns final acceptance | Implicit (§7 Definition of Done) | N/A (playbook doesn't restate ownership per-category) | Explicit (§6 responsibility matrix: "Full and undivided responsibility") | Partially Consistent — stronger in SDLC Playbook than System Prompt |
| No invented dependencies/APIs | Explicit (§5) | Explicit (§4.4 failure modes) | Explicit (§10 guardrail #4–5) | Consistent |
| Idempotency correctness | Explicit, non-negotiable (§3) | Explicit (§4.4 example prompt) | **Contradictory** — §10 states as applied practice; §13 marks partial | **Contradictory** |
| Consumer resilience under outage | Explicit, non-negotiable (§3) | Not directly addressed | §13 marks "Not done" | Partially Consistent — System Prompt's non-negotiable status is not matched by SDLC Playbook's own evidence |
| Secrets handling | **Missing** | Not addressed | Explicit (§10 guardrail #2), and the one place a real violation is disclosed (§13) | Missing in System Prompt, present but shown-violated in SDLC Playbook |
| Transactional correctness (self-invocation risk) | Not addressed (general "match existing code") | Explicit, named as a specific failure mode (§4.4) | Confirmed as a real Section 8 case | Consistent, and the strongest example of documentation matching real evidence |
| Requirement traceability | Not addressed | Not addressed | Explicitly admitted absent (§11) | Consistent in its absence — no artifact claims a matrix exists |

---

## 9. Traceability Audit

Requested chain: Requirement → Prompt → AI Output → Developer Decision → Implementation → Test → Validation → Final Acceptance.

**Where the chain holds, with real evidence:** Implementation → Test → Validation is well-evidenced for the concurrency/double-spend property specifically — real test code, real log output showing a race resolve two different ways on two runs, both consistent with the invariant. This is the one place the full right-hand half of the chain is genuinely traceable.

**Where it breaks:** Requirement → Prompt. There is no requirement-ID register (Playbook §11 states this directly: no traceability matrix exists). Each of the System Prompt's five correctness rules is *addressed* somewhere in the build, but nothing maps "correctness rule #4" to a specific prompt, a specific commit, and a specific test in a way a reviewer could follow without reading the full session transcript.

**Recommended evidence structure**, deliberately lightweight rather than a heavyweight enterprise RTM:

```
| Req ID | Requirement | Prompt/Session ref | Implementation | Test | Status |
|--------|-------------|---------------------|-----------------|------|--------|
| COR-1  | Idempotency, single ledger movement | [session ref] | IdempotencyService | IdempotencyServiceTest | Partial — mocked, not live concurrency |
| COR-2  | Atomic debit/credit | [session ref] | LedgerTransactionService | LedgerTransactionServiceTest | Verified — insufficient-funds path |
| COR-3  | Insufficient funds fails cleanly | [session ref] | PaymentPersistenceService | LedgerTransactionServiceTest | Verified |
| COR-4  | Consumer survives DB outage | [session ref] | KafkaConsumerConfig | NonRetryableExceptionsTest | Partial — classification only, no live outage test |
| COR-5  | No double-spend under concurrency | [session ref] | LedgerTransactionService (row locking) | LedgerTransactionServiceTest (concurrent test) | Verified — strongest evidence in the project |
```

A five-row table costs little and closes most of the traceability gap a reviewer would otherwise have to reconstruct from prose.

---

## 10. Red-Team Findings

| Finding | Risk | Why a reviewer may challenge it | Recommended fix |
|---|---|---|---|
| §10 claims idempotency "verified under genuine concurrency"; §13 admits it wasn't | A reviewer who reads both sections will catch the contradiction in under a minute | Undermines trust in every other claim in the document, by association | Reword §10's practice-table entry to match §13 exactly, or remove it from the "applied practices" framing and move it to a "target practices" list |
| Correctness rule #4 (System Prompt, non-negotiable) was never verified end-to-end | The System Prompt's own language ("non-negotiable") sets an expectation the evidence doesn't meet | "You called this non-negotiable and didn't do it" is a direct, hard question to answer well | Either attempt a real Testcontainers-based DB-kill-mid-consumption test before submission, or explicitly downgrade the System Prompt's language for this one rule with a dated note explaining why |
| A real secret was pasted into a development session | Any AI-governance-literate reviewer will ask directly whether secrets were ever exposed | Silence on this, if a reviewer independently discovers it, is far worse than disclosure | Already disclosed in §13 — no further action needed beyond ensuring it stays disclosed, not quietly removed under submission pressure |
| No requirement traceability matrix exists | The audit brief's own Section 11 asks for this by name | A reviewer specifically trained to ask for one will ask for one | Build the five-to-ten-row table in Section 9 above; low cost, closes a named gap |
| Phase-gate SDLC narrative implies sequential approval; actual commit history shows a full initial scaffold | A reviewer who checks the repository against the document will notice the mismatch immediately | This is the classic "process theater" red flag the audit brief is designed to surface | Already partially disclosed in §11; move the caveat forward to sit beside the claim in §5, not four sections later |
| Section 10's "Constraints enforced by the database, not application code" | Row-level locking in SwiftPay is invoked from application code (`SELECT ... FOR UPDATE` issued by a Java repository method), not a pure database-native constraint like a CHECK or trigger | A reviewer who reads the actual `LedgerTransactionService` code will see application code orchestrating the lock, which is a more precise (and still defensible) description than the current one-line claim | Reword to: "balance sufficiency enforced under a database-issued row lock, invoked from application code" — accurate without losing the point being made |

---

## 11. Reviewer Challenge Questions

| # | Question | Current answer strength |
|---|---|---|
| 1 | How do you know the AI-generated code was correct? | **Strong** — compiled, executed, and tested per Section 7's protocol; the concurrent-transfer test is real, non-deterministic, and passed on repeat runs with different outcomes |
| 2 | What happened when AI generated incorrect code? | **Strong** — Section 8, five real cases with class/method-level specificity |
| 3 | Who made the architecture decisions? | **Strong** — Section 6 matrix; Case 3 shows a specific instance of an embedded AI decision being surfaced and reassigned to the developer |
| 4 | How did you prevent hallucinated APIs or dependencies? | **Adequate** — mostly by compile-and-run rather than doc lookup; this is honestly how the actual defect (Case in §4.4/§10) was caught, and the document should say so plainly rather than imply upfront doc verification was primary |
| 5 | How did you validate generated code? | **Strong** — Section 7's twelve-step protocol, independently evidenced |
| 6 | How did you handle security? | **Weak** — no System Prompt clause; one real incident, disclosed, but the control that should have prevented it didn't exist at the prompt level |
| 7 | How did you protect sensitive information? | **Weak**, same basis as above |
| 8 | Can another engineer reproduce this workflow? | **Adequate** — Section 12's reusable templates support this; full reproduction is limited by the missing requirement-ID register |
| 9 | What evidence proves AI was actually used? | **Strong** — full session transcript, specific enough to name classes and exact log lines |
| 10 | What evidence proves the developer remained in control? | **Strong** — Section 8's cases are specifically about the developer catching the AI, not the reverse |
| 11 | How were prompts improved during development? | **Adequate** — Section 9 documents the pattern generically; no dated before/after example of one specific prompt being revised mid-project exists in evidence |
| 12 | What were the limitations of AI in this project? | **Strong** — Section 2's "three operating rules" and Section 8's root-cause analyses are specific and self-critical |
| 13 | How was the final implementation accepted? | **Adequate** — Section 7's Definition of Done; no single discrete "acceptance" artifact/signature exists, consistent with a solo-developer hackathon context |
| 14 | What would happen if AI generated a plausible but incorrect solution? | **Strong** — this is literally Section 8's subject matter, with a real case (the silently-non-running test suite) that is specifically about plausibility masking a defect |

---

## 12. Priority Remediation Plan

Ranked by how much a fix changes a skeptical reviewer's read, highest first.

1. **Resolve the §10/§13 idempotency contradiction.** Ten-minute fix; highest-visibility internal inconsistency in the document.
2. **Address the unverified non-negotiable correctness rule (consumer resilience).** Either attempt real verification before submission, or explicitly and visibly soften the System Prompt's claim for this one rule with a dated note. Leaving a "non-negotiable" rule unverified, unflagged at the point of claim, is the single biggest exposure in this audit.
3. **Add the five-row requirement traceability table** (Section 9 above). Low cost, directly answers a named gap in the audit brief.
4. **Move the scaffold-vs-phase-gate caveat from §11 into §5**, next to the claim it qualifies.
5. **Add an explicit secrets/security clause to the System Prompt**, informed by the real incident rather than written generically.
6. **Reword §10's "database enforces constraints" line** for precision (Section 10 finding above).

Items 1, 3, and 6 are edits of a few sentences each. Items 2, 4, and 5 require a decision from the developer, not just a rewrite — flagged as such rather than pre-decided here.

---

## 13–15. Corrected Sections

Full rewrites of either document are not warranted — most of both hold up under this audit, and Section 15 of the audit brief itself instructs prioritizing high-impact changes over blanket rewriting. The following are the specific, surgical corrections that resolve the findings above.

### 13. System Prompt — add a new §8 (Security and Sensitive Data)

```markdown
## 8. Security and sensitive data

Never paste credentials, API tokens, connection strings with embedded passwords, or
any production data into a prompt or into generated files. If a command's output
might contain one (e.g. a git remote URL used for authentication), redact it before
it enters the conversation, and say so.

If a secret is pasted anyway, stop and flag it immediately — do not proceed with
the surrounding task first and mention it afterward.
```

### 14. AI Engineering Playbook — correct §10's financial-practices table

Replace:
> Idempotency verified under genuine concurrency | Sequential testing cannot detect the race it must prevent

With:
> Idempotency logic unit-tested against mocked Redis concurrency branches; live concurrent-duplicate verification against a real Redis instance not yet performed — see §13

And add one row:
> Consumer resilience under DB outage | Marked non-negotiable in the System Prompt; classification logic for retry-vs-DLQ is unit-tested, but end-to-end behaviour under a real outage was not verified — see §13

### 15. AI Engineering Playbook — move the scaffold caveat into §5

Insert immediately under the Section 5 heading, before Phase 1:

```markdown
> **Note on how gates actually ran.** The compile → run → test → correct loop in each
> phase below is directly evidenced throughout the build. The five gates are not five
> sequential, separately-committed approvals — the initial commit contains the full
> three-service scaffold, with the loop then applied per-service and per-defect from
> that point forward. Read the phase table as the discipline that governed the whole
> build, not as a literal one-gate-per-commit sequence.
```

---

## 16. Final Submission Checklist

- [ ] §10/§13 idempotency contradiction resolved
- [ ] Consumer-resilience gap either verified or explicitly, visibly softened at the point the non-negotiable claim is made
- [ ] Requirement traceability table added (minimum five rows, one per correctness rule)
- [ ] Scaffold-vs-phase-gate caveat moved forward into §5
- [ ] System Prompt security/secrets clause added
- [ ] §10 database-constraint line reworded for precision
- [ ] Both documents re-read together, back to back, checking specifically for any other claim in one that a later section of the other qualifies or contradicts
