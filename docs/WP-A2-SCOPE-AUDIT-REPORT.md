# WP-A2 Full Scope Audit Report

**Phase ID:** A2 (full work-package scope)
**Phase Objective:** Audit every WP-A2 deliverable against `plan v2.docx` (authoritative), `docs/WP-A2-SUBPLAN.md`, `docs/WP-A2-PHASES.md`, `AGENTS.md`, and the actual repository implementation and tests.
**Date:** 2026-10-25
**Author:** AI backend agent (self-review only; no independent review performed)
**Branch / HEAD:** `backend/wp-a2` / `ca0c9f7f5bd14b7ff7d5680cdadb539145cce6ec`

---

## 1. Preserved reported result (utility acceptance matrix)

Recorded unchanged from the prior cycle:

- **Phase A2 (utility acceptance matrix):** `PASS WITH DOCUMENTED NON-BLOCKING ISSUES`
- `UtilityCalculatorTest`: **38 tests passed, 0 failures, 0 errors, 0 skipped** — BUILD SUCCESS
- Full suite: **52 tests, 0 failures, 0 errors, 1 skipped** (pre-existing `ReleaseGateSuitePlaceholderTest`) — BUILD SUCCESS, exit code 0
- `git diff --check`: clean (exit 0)
- Bytecode/source freshness: partially unproven (class timestamps >= source timestamps; new test names present in bytecode; Maven reported "Nothing to compile")
- Self-review only; no independent review performed

These claims are not strengthened beyond the available evidence.

---

## 2. Audit method

For every requirement I inspected the authoritative source (`plan v2.docx` Sections 2 and 3; `AGENTS.md`; `docs/WP-A2-SUBPLAN.md` traceability table), the implementation files, and the test sources. Status legend:

- **VERIFIED** — implementation present, source-conformant, and exercised by passing tests.
- **PARTIAL** — implementation present but incomplete, non-conformant, or untested.
- **NOT IMPLEMENTED** — required by the plan/sub-plan but absent.
- **BLOCKED** — cannot be completed without an unresolved decision (named).
- **NOT APPLICABLE** — out of WP-A2 scope.

No implementation was inferred from filenames or documentation alone.

---

## 3. Requirement-by-requirement audit

### 3.1 WP-A2-01 — Buyer utility function (plan v2.docx Section 2.1)

- **Source:** plan v2.docx Section 2.1 — hard veto (`P > PmaxB || D > DmaxB || R < RminB || S = 0`), component scores clamped to [0,1], `U_B = wP*uPB + wD*uDB + wR*uRB`, weights `0.60/0.25/0.15`.
- **Implementation:** `services/api/src/main/java/com/payagent/mandates/UtilityCalculator.java` — `buyerUtility`, `component`, `clamp`, `weightedTotal`.
- **Tests:** `UtilityCalculatorTest` — TC-B1..B12, TC-W2. 38/38 green.
- **Status:** VERIFIED. (Rounding HALF_UP at scale 4 verified by TC-B9/TC-B12; clamping verified by TC-B10/TC-W2.)

### 3.2 WP-A2-02 — Seller utility function + convergence (plan v2.docx Section 2.2)

- **Source:** plan v2.docx Section 2.2 — hard veto (`P < PminS || D < DminS || R > RmaxS`), component scores clamped, convergence threshold 0.70, two-party AND rule, "Propose Final Candidate" second path.
- **Implementation:** `UtilityCalculator.sellerUtility`, `isConverged`, `meetsConvergenceThreshold`.
- **Tests:** TC-S1..S8, TC-C1..C8. 38/38 green.
- **Status:** VERIFIED for the threshold path. The "Propose Final Candidate" path is deliberately NOT implemented (no deal state machine exists) and is documented as unresolved (ISSUE-004).

### 3.3 WP-A2-03 — Agreement hash = SHA-256 of RFC-8785 canonical JSON (AGENTS.md Section 4.9)

- **Source:** AGENTS.md Section 4.9; sub-plan Section 9; plan v2.docx Section 3.1 (`termsHashSha256` = SHA-256 of RFC-8785 canonical JSON of `canonicalTerms`).
- **Implementation:**
  - `services/api/src/main/java/com/payagent/agreements/Sha256Hasher.java` — SHA-256 hex digest. Conformant.
  - `services/api/src/main/java/com/payagent/agreements/Rfc8785Canonicalizer.java` — uses Jackson `ORDER_MAP_ENTRIES_BY_KEYS`. **NOT RFC-8785 compliant** (ISSUE-001, unresolved, blocks A5).
  - `services/api/src/main/java/com/payagent/agreements/AgreementService.java` — `proposeAgreement` canonicalizes + hashes + versions; `acceptAgreement` checks hash equality; `proposeNewVersion` delegates to `proposeAgreement`.
  - `services/api/src/main/java/com/payagent/agreements/Agreement.java` — entity with `clearAcceptances()`, `acceptBy(role)`.
- **Tests:** NONE for any agreement component.
- **Gaps:**
  - No RFC-8785 conformance (ISSUE-001 unresolved).
  - `proposeNewVersion` does not explicitly clear prior acceptances on the new version; relies on the new row having null acceptances (works by construction but not verified).
  - `acceptAgreement` does not verify the caller is the correct party (no auth), does not transition the deal to `AGREED`, and has no hash-staleness check beyond equality.
  - No `AgreementController` / REST endpoint (`POST /api/v1/deals/{id}/acceptance` per plan Section 3.3) exists.
  - No tests.
- **Status:** PARTIAL (Sha256 OK; canonicalization non-conformant; no auth, no deal transition, no tests).

### 3.4 WP-A2-04 — Mandate privacy (AGENTS.md Section 4.1)

- **Source:** AGENTS.md Section 4.1 — a party's `MandateDTO` is never serialized to the counterparty in REST or WebSocket; `DealWorkspaceDTO.myMandate` scoped to authenticated user only.
- **Implementation:** `MandateService` has no privacy enforcement (no auth, no deal membership). `MandateController.getMyMandate` hardcodes `PartyRole.BUYER` and returns the requesting party's mandate by that role, but does not verify the caller is that party on that deal.
- **Tests:** NONE.
- **Status:** NOT VERIFIED. The invariant is documented but not implemented or tested. No counterparty-scoping test exists.

### 3.5 WP-A2-05 — Idempotency (AGENTS.md Section 4.6)

- **Source:** AGENTS.md Section 4.6 — all mutating endpoints require `Idempotency-Key`; stored in `idempotency_keys` table with request hash.
- **Implementation:** `MandateController.createMandate` checks only that the `Idempotency-Key` header is present (returns 400 if absent) — placeholder. No `idempotency_keys` table integration, no request-hash comparison, no 409 on reuse-with-different-payload, no transactional atomicity with the mandate row.
- **Tests:** NONE.
- **Status:** NOT IMPLEMENTED for mandates.

### 3.6 WP-A2-06 — Object-level authorization (AGENTS.md Section 4.2)

- **Source:** AGENTS.md Section 4.2 — every deal/mandate access checked against authenticated deal membership and role; cross-account access returns 403.
- **Implementation:** No deal membership verification anywhere (`MandateService` has no deal guard; `MandateController` hardcodes `PartyRole.BUYER` with a TODO). No `Deal` entity or `DealService` exists in the repository at all.
- **Tests:** NONE (no cross-account negative tests).
- **Status:** NOT IMPLEMENTED. Blocked on a deal-membership mechanism (sub-plan Section 12: "How to derive PartyRole from JWT + deal membership without DealService").

### 3.7 WP-A2-07 — Mandate endpoints (ARCHITECTURE.md Section 2.1)

- **Source:** ARCHITECTURE.md Section 2.1 — `POST /api/v1/deals/{id}/mandates`, `GET /api/v1/deals/{id}/mandates/me`.
- **Implementation:** `MandateController` implements both paths (lines 22-43). `POST` creates a mandate; `GET /me` retrieves the latest. Both use placeholder auth (hardcoded BUYER role) and placeholder idempotency.
- **Tests:** NONE (no integration tests).
- **Status:** PARTIAL — endpoints exist but are not production-ready (no auth, no idempotency, no deal membership, no validation of delivery/revisions fields).

### 3.8 WP-A2-08 — Mandates table unique constraint (V1__init.sql)

- **Source:** sub-plan WP-A2-08; `V1__init.sql` lines 21-40 — `UNIQUE (deal_id, party_role, version)`.
- **Implementation:** `Mandate` entity declares `@UniqueConstraint(columnNames = {"deal_id","party_role","version"})`. Schema and entity agree.
- **Tests:** None at runtime; verified by schema inspection only.
- **Status:** VERIFIED at schema/entity level; not exercised by a runtime test.

### 3.9 Mandate domain — additional sub-plan requirements (sub-plan Sections 3, 5, 7, 8)

| Requirement (sub-plan) | Implementation status | Evidence |
|---|---|---|
| JPA entity maps to `mandates` table columns | PARTIAL | `Mandate.java` maps all columns; JSONB stored as `String` with hand-rolled `toJson` (sub-plan Section 12 flags this as insufficient). |
| Validate version >= 1 | VERIFIED (service) | `MandateService.createMandate` checks `dto.version() < 1`. |
| Validate expiresAt > now | VERIFIED (service) | `MandateService` parses and checks `expires.isBefore(now)`. |
| Buyer: limitPrice >= targetPrice; Seller: limitPrice <= targetPrice | VERIFIED (service) | `MandateService` price-direction check per role. |
| Validate delivery/revisions fields + target-vs-limit ordering | NOT IMPLEMENTED | U-4 open; only price fields range-checked. |
| Uniqueness enforced at DB level | VERIFIED (schema) | V1__init.sql + @UniqueConstraint. |
| Authenticated user identity from JWT, never trust client-supplied ID | NOT IMPLEMENTED | Controller hardcodes `PartyRole.BUYER`; no JWT resolution. |
| `getMyMandate` verifies deal membership | NOT IMPLEMENTED | No deal guard exists. |
| Never expose counterparty mandate | NOT TESTED / not enforced at service layer | Privacy relies on controller scoping (hardcoded role — insecure). |
| Idempotency-Key required for POST; 409 on reuse; 422 on invalid | NOT IMPLEMENTED (placeholder) | Controller returns 400 if header missing; no 409/422 semantics; no idempotency_keys integration. |
| Transaction boundaries (mandate create + idempotency record atomic) | NOT IMPLEMENTED | `@Transactional` on createMandate but no idempotency record. |
| MandateDTO round-trips mustHave/forbidden terms | DEFECT | `MandateService.toDto` returns `java.util.List.of()` placeholders — data loss on read. |
| JSONB deserialization for mustHave/forbidden | NOT IMPLEMENTED | Sub-plan Section 12 flags the simple string builder as insufficient. |

### 3.10 Agreement domain — additional sub-plan requirements (sub-plan Sections 3, 6, 9)

| Requirement (sub-plan) | Implementation status | Evidence |
|---|---|---|
| RFC-8785 JSON Canonicalization Scheme | NOT CONFORMANT | `Rfc8785Canonicalizer` uses Jackson sorted keys only (ISSUE-001). |
| SHA-256 hex digest 64 chars | VERIFIED | `Sha256Hasher.hash` uses `MessageDigest` SHA-256 + `HexFormat` (64 hex chars). |
| New version on terms change; clear prior acceptances | PARTIAL | `proposeAgreement` increments version (new row). `proposeNewVersion` delegates and relies on the new row having null acceptances; no explicit clear; no race guard. |
| Accept only if version and hash match | PARTIAL | `acceptAgreement` checks hash equality; does not verify caller party or deal membership. |
| Acceptance by correct party only | NOT IMPLEMENTED | `acceptBy(String role)` trusts the caller-supplied role string; no auth. |
| Deal transitions to AGREED only after both parties accept identical version/hash | NOT IMPLEMENTED | No deal state machine / no `Deal` entity exists. |
| Transactional consistency | PARTIAL | `@Transactional` present; no multi-entity atomicity tested. |
| REST endpoint `POST /api/v1/deals/{id}/acceptance` | NOT IMPLEMENTED | No `AgreementController`. |
| Integration tests (propose/accept, version bump, stale hash, single acceptance) | NOT IMPLEMENTED | No agreement tests at all. |

### 3.11 Additional WP-A2 requirements

- **Deal state machine (plan v2.docx Section 1, Section 3.1):** NOT IMPLEMENTED. No `deals` package, no `Deal` entity, no `DealService`. `DealStatus` enum exists in contracts but is unused in code. `DealWorkspaceDTO` exists but is not produced anywhere. This blocks mandate deal-membership verification and agreement deal transitions. (Likely Phase A3/A4/A6 scope, but it is a WP-A2-adjacent dependency.)
- **Canonical contracts parity (plan v2.docx Section 3):** VERIFIED — `ContractTypeScriptParityTest` and `ContractFixtureDeserializationTest` pass (part of the 52-test suite).
- **Auth baseline (WP-A2-06/07):** `JwtAuthFilter`, `UserDetailsServiceImpl`, `SecurityConfig` exist; `AuthIntegrationTest` (7 tests) and `JwtTokenProviderTest` (3 tests) pass. Auth itself is functional; deal-scoped authorization is not.

---

## 4. Test evidence summary

| Test class | Tests | Failures | Errors | Skipped | Notes |
|---|---|---|---|---|---|
| `UtilityCalculatorTest` | 38 | 0 | 0 | 0 | WP-A2-01/02 verified |
| `AuthIntegrationTest` | 7 | 0 | 0 | 0 | auth baseline |
| `JwtTokenProviderTest` | 3 | 0 | 0 | 0 | auth baseline |
| `ContractFixtureDeserializationTest` | 1 | 0 | 0 | 0 | contracts |
| `ContractTypeScriptParityTest` | 1 | 0 | 0 | 0 | contracts |
| `HealthControllerTest` | 1 | 0 | 0 | 0 | health |
| `ReleaseGateSuitePlaceholderTest` | 1 | 0 | 0 | 1 | pre-existing, by design |
| **Total** | **52** | **0** | **0** | **1** | BUILD SUCCESS, exit 0 |

No tests exist for `MandateService`, `MandateController`, `MandateRepository`, `Mandate` entity, `AgreementService`, `AgreementRepository`, `Agreement` entity, `Rfc8785Canonicalizer`, or `Sha256Hasher`.

---

## 5. Gate resolution

### 5.1 What is verified

- UtilityCalculator (WP-A2-01, WP-A2-02): fully verified, source-conformant, tested.
- Canonical contracts (plan Section 3): parity tests pass.
- Auth baseline: functional and tested.
- Mandates table unique constraint (WP-A2-08): schema/entity level.
- Sha256Hasher: implementation conformant (untested).

### 5.2 What is missing or unverified

1. MandateService (WP-A2-04, 05, 06, 07): partial skeleton — no auth, no deal membership, no idempotency, validation gaps (U-4), `toDto` data loss (lists dropped), no tests.
2. AgreementService (WP-A2-03): partial skeleton — non-RFC-8785 canonicalization (ISSUE-001), no auth, no deal-state transition, no controller, no tests.
3. Deal state machine: not implemented (blocks mandate deal-membership and agreement transitions).
4. Idempotency integration: not implemented for mandates.
5. No integration tests for mandate/agreement domains.

### 5.3 Decision

**WP-A2 as a whole is NOT complete.** Only the UtilityCalculator sub-scope (WP-A2-01, WP-A2-02) is verified. The mandate and agreement domains are partial skeletons with no tests and depend on unresolved decisions (ISSUE-001 RFC-8785 library, ISSUE-003 untracked-files handling, deal-membership mechanism, idempotency details, JSONB serialization).

**No implementation is authorized in this cycle beyond the already-authorized utility work.** The mandate/agreement gaps are Phase A3/A5/A6 scope and require the unresolved decisions above to be settled first. They are not silently resolved here.

**Phase A3 must NOT begin.** WP-A2 exit criteria (sub-plan Section 13) are not met: mandate endpoints do not enforce validation/privacy/auth/idempotency; agreement proposal is not RFC-8785-conformant and is untested; mandate data leak risk is unverified.

---

## 6. Smallest necessary decisions to unblock

1. **ISSUE-001** (RFC-8785 library) — approval of a maintained Java RFC-8785 implementation. Blocks A5/A6 and agreement hash conformance.
2. **ISSUE-003** (untracked files) — retain or discard the existing skeleton. Affects whether the mandate/agreement skeletons are refactored or replaced.
3. **Deal-membership mechanism** (sub-plan Section 12) — how to derive `PartyRole` from JWT + deal without a `DealService`. Required for WP-A2-04/06/07.
4. **Idempotency details** (sub-plan Section 12) — integration of `idempotency_keys` for mandate creation.
5. **JSONB serialization** (sub-plan Section 12) — replace hand-rolled `toJson` with Jackson for `mustHaveDeliverables`/`forbiddenTerms`.
6. **MandateService `toDto` data loss** — parse JSONB lists instead of returning empty lists.

Until (1)-(3) are settled, the mandate/agreement domains cannot be completed to plan.

---

## 7. Next authorized action

No Phase A3 start. The next authorized action is to resolve decisions (1)-(3) above (user approval required), then execute Phase A3 (Mandate Domain Model & Repository) per `docs/WP-A2-PHASES.md`, which is the first phase that touches the mandate domain with a defined, testable scope. Phase A3 preconditions include Phase A2 PASS — which is NOT yet satisfied; therefore the phase-breakdown precondition itself must be re-evaluated before any mandate-domain work begins.

---

## 8. Git state

- Branch `backend/wp-a2`, HEAD `ca0c9f7f5bd14b7ff7d5680cdadb539145cce6ec` (unchanged).
- Nothing staged, committed, pushed, stashed, reset, cleaned, or branch-switched.
- All untracked WP-A2 work preserved.
- `git diff --check`: clean.

---

## 9. Status summary

- UtilityCalculator sub-scope (WP-A2-01, WP-A2-02): **PASS WITH DOCUMENTED NON-BLOCKING ISSUES** (self-review only).
- Full WP-A2 work package: **NOT COMPLETE** — mandate and agreement domains are partial/untested; deal state machine absent.
- Phase A3: **NOT AUTHORIZED TO BEGIN.**
