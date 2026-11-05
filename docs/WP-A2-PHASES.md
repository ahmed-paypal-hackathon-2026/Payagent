# WP-A2 Phase Breakdown

**Work Package:** WP-A2  
**Source:** `docs/WP-A2-SUBPLAN.md`  
**Status:** Planning — awaiting approval

## Phase Overview

Phases are ordered by dependency, risk, and verifiability. No implementation may start without explicit approval of the sub-plan and this phase breakdown.

## Phase A1 — Foundation Verification

**Objective:** Confirm repository baseline, auth, contracts, and test harness are suitable for WP-A2.

**Requirements covered:** WP-A2-06, WP-A2-07

**Preconditions:**
- Branch `backend/wp-a2` exists at verified `origin/main`
- No uncommitted changes to production code; planning documents may exist as untracked files
- Existing untracked WP-A2 implementation skeleton files must be documented but not modified in this phase

**Scope:**
- Verify `JwtAuthFilter`, `UserDetailsServiceImpl`, `SecurityConfig` permit health/auth, require auth otherwise
- Verify `ContractTypeScriptParityTest` and `ContractFixtureDeserializationTest` pass
- Verify test profile `application-test.yml` uses H2, Flyway disabled
- Document existing idempotency infrastructure
- Inventory untracked implementation files and record decision on retention

**Expected files affected:** None — read-only; planning artifacts may be read

**Verification commands:**
```
./mvnw -B test -Dtest=ContractTypeScriptParityTest,ContractFixtureDeserializationTest,JwtTokenProviderTest
git status --short
git diff --check
```

**Acceptance criteria:**
- All listed tests PASS
- No uncommitted changes to tracked production code; `git diff --check` passes for tracked files
- Auth and contract baseline documented
- Inventory of untracked WP-A2 implementation files recorded in issues log

**PASS:** All criteria met
**FAIL:** Tests fail or baseline verification fails
**BLOCKED:** Untracked implementation files cannot be documented without modifying them

**Risks:** Baseline drift if main advances

## Phase A2 — UtilityCalculator Implementation & Verification

**Objective:** Implement deterministic utility calculation per plan §2.

**Requirements:** WP-A2-01, WP-A2-02

**Preconditions:** Phase A1 PASS

**Scope:**
- Implement `UtilityCalculator` with BigDecimal, weights, clamping, veto, convergence
- Unit tests for buyer/seller, edge cases, zero denominator, rounding

**Files:**
- `services/api/src/main/java/com/payagent/mandates/UtilityCalculator.java`
- `services/api/src/test/java/com/payagent/mandates/UtilityCalculatorTest.java`

**Verification:**
```
./mvnw -B test -Dtest=UtilityCalculatorTest
```

**Acceptance:** 100% of defined scenarios pass, no floating point arithmetic

## Phase A3 — Mandate Domain Model & Repository

**Objective:** JPA entity, repository, validation.

**Requirements:** WP-A2-05, WP-A2-07

**Preconditions:** Phase A2 PASS

**Scope:**
- Mandate entity mapping to `mandates` table
- Repository with finder methods
- Validation: version, expiresAt, price direction per role
- JSONB handling for lists via Jackson

**Exclusions:** Controller, auth integration

**Acceptance:** Repository tests pass, validation rejects invalid data

## Phase A4 — Mandate Service & Authorization

**Objective:** Service layer with auth, privacy, idempotency.

**Requirements:** WP-A2-04, WP-A2-05, WP-A2-06

**Preconditions:** Phase A3 PASS, deal membership verification mechanism exists or is approved for implementation

**Scope:**
- `MandateService.createMandate` with idempotency check
- `getMyMandate` with deal membership verification
- Never expose counterparty mandate
- Error semantics 422/409/403

**Tests:** Integration tests with mock auth, cross-account negative tests

## Phase A5 — Agreement Canonicalization & Hashing

**Objective:** RFC-8785 canonicalization and SHA-256 hashing.

**Requirements:** WP-A2-03

**Preconditions:** Approval of canonicalization library choice

**Scope:**
- `Rfc8785Canonicalizer` using approved library
- `Sha256Hasher`
- Tests for deterministic output and known vectors

**Risks:** Library selection unresolved

## Phase A6 — Agreement Service & Dual Acceptance

**Objective:** Proposal, versioning, acceptance, state integrity.

**Requirements:** WP-A2-03

**Preconditions:** Phase A5 PASS

**Scope:**
- `AgreementService.proposeAgreement`
- `acceptAgreement` with hash verification
- Version bump clears prior acceptances
- Transactional consistency

**Tests:** Propose/accept same terms works; changed terms creates new version; stale hash rejected; single acceptance does not transition to AGREED

## Phase A7 — Integration & Regression

**Objective:** End-to-end mandate → utility → agreement lifecycle.

**Requirements:** All

**Preconditions:** Phases A2-A6 PASS

**Scope:**
- Integration tests combining mandates, utility, agreements
- Contract parity tests remain green
- Security review for mandate privacy and IDOR
- Full test suite `./mvnw -B test`

**Acceptance:** Zero failures, no security findings, `git diff --check` passes

## Reporting

Each phase produces a per-phase report per template. Cumulative reports maintained in `docs/WP-A2-IMPLEMENTATION-REPORT.md` and `docs/ISSUES-DECISIONS-LOG.md`.

## Change Control

Phase breakdown is immutable after approval. Any change requires documented revision with explicit user approval.
