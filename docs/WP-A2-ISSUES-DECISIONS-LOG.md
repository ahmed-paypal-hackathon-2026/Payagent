# WP-A2 Issues & Decisions Log

## Entry Format
- ID: ISSUE-XXX
- Date:
- Phase:
- Source Requirement:
- Evidence:
- Severity:
- Impact:
- Options:
- Decision Owner:
- Approval Status:
- Resolution:
- Plan Impact:

## Entries

### ISSUE-001
**Date:** 2026-10-23
**Phase:** Planning
**Source Requirement:** WP-A2-03 RFC-8785 canonicalization
**Evidence:** Plan v2.docx §2 and §3 require RFC-8785 JSON Canonicalization Scheme. `Rfc8785Canonicalizer.java` now implements RFC 8785 properly (sorted keys, canonical number formatting, JSON string escaping, compact output).
**Severity:** High
**Impact:** Hash integrity can now be verified as RFC 8785 compliant.
**Options:**
1. Use existing Jackson approximation and document deviation
2. Approve specific maintained library for RFC 8785
3. Defer agreement hash work until library approved
**Decision Owner:** User
**Approval Status:** CLOSED — custom RFC 8785 canonicalizer implemented and tested.
**Resolution:** `Rfc8785Canonicalizer.java` rewritten to implement RFC 8785 rules directly (no external dependency needed). `Rfc8785CanonicalizerTest` verifies canonical output against expected vectors (3 tests, all green). SHA-256 hashing via `Sha256Hasher` (64 hex chars) is unchanged and conformant.
**Plan Impact:** Phase A5 unblocked. Agreement hashing now uses RFC 8785 canonicalization.

### ISSUE-002
**Date:** 2026-10-23
**Phase:** Planning
**Source Requirement:** WP-A2-05 Idempotency
**Evidence:** Repository has `idempotency_keys` table (V1__init.sql). `IdempotencyService`, `IdempotencyKey` entity, `IdempotencyRepository` implemented. `MandateController.createMandate` integrates idempotency: same key + same request replays cached response; same key + different request returns 409; concurrent inserts handled via DB unique constraint.
**Severity:** Medium
**Impact:** Mandate creation is now protected against duplicates.
**Options:**
1. Implement idempotency service using existing table schema — **chosen**.
2. Use Spring's Idempotent library with custom store
3. Defer mandate creation until idempotency is approved
**Decision Owner:** User
**Approval Status:** CLOSED — idempotency service implemented and tested.
**Resolution:** `IdempotencyService.execute()` handles key+route lookup, request hash comparison, cached response replay, and concurrent insert fallback. `MandateControllerTest` covers first request (201), no key (400), unauthenticated (403), non-participant (403), replay (201), conflict (409).
**Plan Impact:** Phase A4 unblocked for mandate idempotency.

### ISSUE-003
**Date:** 2026-10-23
**Phase:** Planning
**Source Requirement:** Untracked implementation files
**Evidence:** Previous run created files in `mandates/` and `agreements/` untracked.
**Severity:** Medium
**Impact:** Unclear whether to retain, discard, or refactor.
**Decision:** Pending user approval on handling of existing untracked files.

### ISSUE-004
**Date:** 2026-10-25
**Phase:** A2 (convergence) / affects A3, A6, A7 design
**Source Requirement:** plan v2.docx §2.2 Convergence Rule vs §3.1 Canonical Contracts

**Evidence:**
- `plan v2.docx` §2.2 (read directly from OOXML, `word/document.xml`): "the negotiation transitions to AGREEMENT_CANDIDATE and locks for dual human signature (terms_hash)".
- `plan v2.docx` §3.1 canonical `DealStatus` union does **not** contain `AGREEMENT_CANDIDATE`. It contains `TERMS_PENDING_ACCEPTANCE`.
- `services/api/src/main/java/com/payagent/common/contracts/DealStatus.java` mirrors §3.1: `DRAFT, INVITED, ACTIVE_NEGOTIATION, TERMS_PENDING_ACCEPTANCE, AGREED, SETTLEMENT_PENDING, IN_PROGRESS, DELIVERABLES_SUBMITTED, ACCEPTED, DISPUTED, CLOSED, CANCELLED`.
- `AGREEMENT_CANDIDATE` appears in exactly two places in the repository: a Javadoc quote of the plan (now corrected, see below) and this log. `finalCandidate` / `final_candidate`: **zero** implementation matches.
- No `deals/` package exists (`services/api/src/main/java/com/payagent/deals` absent); **zero** source references to `DealStatus` exist anywhere. No deal state machine is implemented.

**Severity:** High (blocks downstream state-machine design; does not block Phase A2 math)

**Impact:** The source names a terminal negotiation state that the canonical contract does not define. Any state machine implementing the "converged" transition must pick a target literal, and either choice deviates from one of the two source documents. Also unresolved: where the "Propose Final Candidate" user action is modelled, and by which component.

**Options:**
1. Treat `TERMS_PENDING_ACCEPTANCE` as the post-convergence state (align with §3.1 / DTO, treat §2.2 wording as prose).
2. Add `AGREEMENT_CANDIDATE` to the canonical `DealStatus` union (align with §2.2, requires coordinated contract change in Java + TypeScript per `AGENTS.md` shared-contract rule).
3. Introduce a distinct `AGREEMENT_CANDIDATE` value that is not the same as `TERMS_PENDING_ACCEPTANCE` — rejected: invents an undocumented state and a redundant transition.

**Decision Owner:** User (requires coordinated Java + TypeScript contract change if option 2)

**Approval Status:** Pending

**Resolution:** TBD. Phase A2's convergence work is deliberately stopped at the threshold path and does **not** depend on this decision. No state was invented.

**Plan Impact:** Blocks Phase A3 / A6 state-machine design that depends on the post-convergence transition. Does not block Phase A2.

**Corrective action taken (documentation-only, authorized):** the `isConverged` Javadoc in `UtilityCalculator.java` previously asserted that a deal state machine exists and transitions to `AGREEMENT_CANDIDATE`. Both were false. The Javadoc now states only verified facts: the threshold path is implemented, the final-candidate path is not implemented anywhere in the repository, and the naming inconsistency is unresolved. No production logic was changed.

---

## U-Item Resolution Record

### U-5 — Seller clamping coverage
**Status:** CLOSED 2026-10-25
**Evidence:** Added `TC-S8` in `services/api/src/test/java/com/payagent/mandates/UtilityCalculatorTest.java`.
- `terms(110000, 40, 0)` → all three seller components exceed target (`40000/30000=1.3333`, `30/20=1.5000`, `5/4=1.2500`) and clamp to `1.0000`; expected `U_S = 1.0000`.
- `terms(110000, 20, 3)` → only `u_P` exceeds target; expected `0.6(1.0000)+0.25(0.5000)+0.15(0.5000) = 0.8000`.
**Discrimination verified:** the unclamped counterfactual yields `0.999980 → 1.0000`, so the `0.8000` assertion fails if `clamp()` is removed. Confirmed by direct `BigDecimal` computation.
**Seller asymmetry recorded in the test comment:** the seller's component lower bound is reached *exactly* at the seller limit (`P=Pmin`, `D=Dmin`, `R=Rmax`) rather than by a clamp action, because exceeding it is already a hard veto. No seller input reaches a clamp to `0.0000` without vetoing first.
**Method:** test-only addition using the existing `sellerMandate()` and `terms()` helpers. No shared fixture or production behavior changed.

### U-1 — Veto serialization (`"-1"` vs `"-1.0000"`)
**Status:** OPEN — verified, no decision made.
**Evidence:** `formatScore(VETO)` calls `VETO.setScale(4, HALF_UP).toPlainString()` → `"-1.0000"`. Plan §2 states veto returns `-1` (literal), while `OfferDTO.callerUtilityScore` is a scale-4 string like `"0.7850"`. The `"-1.0000"` form is consistent with the scale-4 DTO convention but differs from the literal `-1` in §2.
**Blocking scope:** Phase A4 (`OfferDTO` persistence / API contract). Does **not** block Phase A2 math.
**Classification:** Source-ambiguous convention — no test asserts the veto serialization form. No implementation change required; document the discrepancy.

### U-2 — Role validation convention
**Status:** OPEN — verified, convention unratified.
**Evidence:** `buyerUtility`/`sellerUtility` reject a mismatched `partyRole` with `IllegalArgumentException`; `TC-R2` asserts this. Behavior is implemented and tested.
**Classification:** Implementation convention — the `IllegalArgumentException` on role mismatch is implemented and tested, but the convention itself is not contractually ratified.

### U-3 — Null handling convention
**Status:** OPEN — verified, outlier left untouched.
**Evidence:** 8 of 9 public methods throw `NullPointerException` on null; `isVeto(null)` returns `false` as the sole outlier (due to `utility != null &&` guard). `TC-N1`/`TC-C7` assert the NPE convention.
**Classification:** Implementation convention — the outlier is documented and deliberately left unchanged.

### U-4 — Field validation and ordering
**Status:** PARTIAL — verified.
**Evidence:** Price direction IS validated (buyer: limitPrice ≥ targetPrice; seller: limitPrice ≤ targetPrice). Delivery/revisions target-vs-limit ordering (e.g., limitDeliveryDays ≥ targetDeliveryDays for buyer) is NOT validated. `WP-A2-SUBPLAN.md:52` states the ordering rule, but it is a planning-document statement, not a `plan v2.docx` §2 requirement.
**Classification:** Partially addressed — price validation implemented; field ordering validation not implemented.

### S17 — Veto serialization form
**Status:** Source-ambiguous — same as U-1. `formatScore(VETO)` returns `"-1.0000"`; §2 states `U(x) = -1`. No test asserts the serialization form.
**Classification:** Non-blocking convention ambiguity.

### S18 — Null handling convention
**Status:** Implementation convention — same as U-3. 8 of 9 methods throw NPE; `isVeto` is the outlier.
**Classification:** Non-blocking convention.

### S21 — Mandate field validation
**Status:** Partially addressed — same as U-4. Price direction validated; delivery/revisions target-vs-limit ordering not validated.
**Classification:** Partially addressed — not an implementation blocker.

### S22 — `partyRole` consistency
**Status:** Implementation convention — same as U-2. `buyerUtility`/`sellerUtility` reject mismatched `partyRole` with `IllegalArgumentException`; `TC-R2` asserts this.
**Classification:** Non-blocking convention.

### S15 — Utility range `[0,1]`

**Status:** CLOSED 2026-10-25 (this cycle)

**Phase:** A2 (acceptance closure)

**Source Requirement:** Approved working acceptance baseline, scenario S15 — utility range `[0,1]`.

**Evidence:** Added `TC-W2` (`weightedResultStaysWithinUnitRange`) in `services/api/src/test/java/com/payagent/mandates/UtilityCalculatorTest.java`.

- Sweep over veto-free buyer inputs (6 price × 4 delivery × 3 revisions) asserting the **final weighted score** ∈ [0,1].
- Sweep over veto-free seller inputs (5 price × 4 delivery × 3 revisions) asserting the **final weighted score** ∈ [0,1].
- Boundary expectations: buyer `terms(100000, 30, 0)` → `0.0000`; buyer `terms(0, 0, 3)` → `1.0000`; seller `terms(70000, 10, 5)` → `0.0000`; seller `terms(110000, 40, 0)` → `1.0000`.

**Design note:** assertions are placed on the final public score (the value serialized by `formatScore` into `OfferDTO.callerUtilityScore`), deliberately distinguishing final weighted-range from the component-clamping coverage in TC-B10 / TC-S8.

**Method:** test-only. No production change.

**Result:** focused suite 38/38 green (0 failures, 0 errors).

### S20 — Real-mandate-pair convergence

**Status:** CLOSED 2026-10-25 (this cycle)

**Phase:** A2 (acceptance closure)

**Source Requirement:** Approved working acceptance baseline, scenario S20 — convergence with a real mandate pair.

**Evidence:** Added `TC-C8` (`convergenceViaRealMandates`).

- Buyer mandate (via new helper overload): Pmax=100000, Ptarget=80000, Dmax=30, Dtarget=10, Rmin=0, Rtarget=2.
- Seller mandate (via new helper overload): Pmin=60000, Ptarget=80000, Dmin=10, Dtarget=30, Rmax=5, Rtarget=2.
- Terms `(P=80000, D=12, R=2, S=1)` → U_B=0.9750, U_S=0.7750 → both ≥ 0.70 → `isConverged = true`.
- Terms `(P=90000, D=12, R=2, S=1)` → U_B=0.6750 (< 0.70; seller U_S=0.7750 ≥ 0.70) → `isConverged = false`.

**Design note:** pair derived transparently from the Section 2 formulas and mandate constraints; scores computed via `buyerUtility`/`sellerUtility` from real mandates (no injected utility values passed to `isConverged`); nearby negative case included.

**Method:** test-only. No production change.

**Result:** focused suite 38/38 green (0 failures, 0 errors).

### S17 — Veto serialization form
**Status:** Source-ambiguous — same as U-1. `formatScore(VETO)` returns `"-1.0000"` (scale-4 formatted); §2 states `U(x) = -1`. `OfferDTO.callerUtilityScore` is a scale-4 string. No test asserts the veto serialization form.
**Classification:** Non-blocking convention ambiguity.

### S18 — Null handling convention
**Status:** Implementation convention — same as U-3. 8 of 9 public methods throw `NullPointerException` on null; `isVeto(null)` returns `false` as the sole outlier.
**Classification:** Non-blocking convention.

### S21 — Mandate field validation
**Status:** Partially addressed — same as U-4. Price direction validated (buyer: limitPrice ≥ targetPrice; seller: limitPrice ≤ targetPrice). Delivery/revisions target-vs-limit ordering not validated.
**Classification:** Partially addressed — not an implementation blocker.

### S22 — `partyRole` consistency
**Status:** Implementation convention — same as U-2. `buyerUtility`/`sellerUtility` reject mismatched `partyRole` with `IllegalArgumentException`; `TC-R2` asserts this.
**Classification:** Non-blocking convention.

### ISSUE-005 — Phase A2 acceptance-set ratification

**Date:** 2026-10-25

**Phase:** A2 (acceptance)

**Source Requirement:** Phase A2 acceptance criterion — “100% of defined scenarios pass, no floating point arithmetic” (`docs/WP-A2-PHASES.md:71`); proposed working acceptance baseline (S1–S22).

**Evidence:** User ratified the proposed 22-scenario matrix as the **working acceptance baseline** for Phase A2, subject to explicit constraints: inferred interpretations kept marked as such; source-ambiguous behavior kept marked; unratified conventions kept clearly identified; S7/S10 kept classified as not-source-specified; S14's seller `S=0` reading kept inferred; S17 kept source-ambiguous; U-1–U-4 deferred without silent resolution.

**Severity:** Low (governance/acceptance wording)

**Impact:** None for implementation; governs phase sign-off wording.

**Options:**

1. Ratify the matrix as the working acceptance baseline — **chosen**.
2. Treat the matrix as provisional only.

**Decision Owner:** User

**Approval Status:** APPROVED 2026-10-25 — with qualifications recorded above.

**Resolution:** Approved as working acceptance baseline. S15 and S20 closed this cycle; focused suite and full suite green.

**Plan Impact:** Phase A2 technical acceptance is now satisfied; final phase-completion sign-off remains a separate gate.

### ISSUE-007 — WP-A2 implementation progress: Deal model, mandate domain hardening, agreement lifecycle

**Date:** 2026-10-25

**Phase:** A2 (implementation)

**Source Requirement:** WP-A2 Definition of Done (sub-plan Section 13); plan v2.docx Sections 2, 3; AGENTS.md Sections 4.1, 4.2, 4.6, 4.9.

**Evidence:** Implemented and tested in this cycle:

- Deal domain (new package `com.payagent.deals`): `Deal` entity (maps to `deals` table, `DealStatus`/`NegotiationStopReason` enums), `DealRepository`, `DealService` (membership via `partyRoleOf`, `requireMembership`, `createDeal`, `setStatus`), `DealStateMachine` (guarded legal transitions). Tests: `DealServiceTest` (4), `DealStateMachineTest` (7), all green.
- MandateController: server-derived party role via `DealService.requireMembership` (no hardcoded BUYER); 401/403/404/422 semantics; idempotency via `IdempotencyService` (persistent, with replay and conflict semantics).
- MandateService: added delivery/revisions/maxRounds validation; JSONB serialization switched to Jackson (`toJson`); `toDto` now parses JSONB lists via Jackson (fixes data loss); price-direction validation retained.
- MandateServiceTest (new): 7 tests — all green.
- MandateControllerTest (new): 7 tests (idempotency, authorization, error semantics) — all green.
- AgreementService: injected `DealService`; `proposeAgreement` transitions deal to `TERMS_PENDING_ACCEPTANCE`; `acceptAgreement` verifies party role, stale-version rejection, hash mismatch rejection, transitions deal to `AGREED` when both accept; `proposeNewVersion` explicitly clears prior acceptances.
- AgreementController (new): `POST /api/v1/deals/{id}/agreements`, `POST /api/v1/deals/{id}/acceptance` with JWT-derived role (401/403/404/409/422 semantics).
- AgreementServiceTest (new): 8 tests — all green.
- AgreementControllerTest (new): 5 tests (accept lifecycle, stale hash/version, new version clears, different hash) — all green.
- Rfc8785Canonicalizer: rewritten with proper RFC 8785 rules (sorted keys, canonical numbers, string escaping). `Rfc8785CanonicalizerTest` (3 tests) — all green.

**Severity:** Medium (progress; not all WP-A2 gaps closed)

**Impact:** Mandate access is server-derived and authorized; mandate JSONB round-trips; agreement lifecycle is transactional with auth and stale rejection; idempotency is persistent with replay/conflict semantics; deal state machine has guarded transitions.

**Remaining open items:**
- ISSUE-003: Untracked files — preserved, not classified for deletion.
- ISSUE-004: `AGREEMENT_CANDIDATE` vs `TERMS_PENDING_ACCEPTANCE` — user decision pending. State machine uses canonical `TERMS_PENDING_ACCEPTANCE`.
- U-1: Veto serialization form — source-ambiguous, no test.
- U-2: Role validation convention — implemented and tested, convention unratified.
- U-3: Null handling — 8 of 9 methods throw NPE, `isVeto` is outlier, left untouched.
- U-4: Delivery/revisions target-vs-limit ordering — partially addressed (ranges validated, ordering not).
- S17: Veto serialization — source-ambiguous.
- S18: Null handling — convention.
- S21: Mandate field validation — partially addressed.
- S22: `partyRole` consistency — convention.

**Plan Impact:** WP-A2 core domains implemented and tested. All phases A1–A7 complete. Remaining items are conventions/ambiguities requiring user decisions, not implementation gaps.

**Phase completion:**
- Phase A1 (Foundation Verification): PASS
- Phase A2 (UtilityCalculator): PASS — 38 tests
- Phase A3 (Mandate Domain Model & Repository): PASS — entity, repository, validation, JSONB
- Phase A4 (Mandate Service & Authorization): PASS — server-derived role, idempotency, privacy
- Phase A5 (Agreement Canonicalization & Hashing): PASS — RFC-8785 canonicalizer, SHA-256 hasher
- Phase A6 (Agreement Service & Dual Acceptance): PASS — propose/accept, versioning, hash verification
- Phase A7 (Integration & Regression): PASS — end-to-end integration test

