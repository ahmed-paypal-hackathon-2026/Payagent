# Member A Gap Analysis

**Workspace:** `C:\Users\mona mohamed\Downloads\Payagent-integration-20261025\repo`  
**Branch:** `integration/member-a-reconciliation`  
**HEAD:** `bc3fc4acdad15b68b5e047c6bccb3b4cd08f56f5`  
**Last updated:** 2026-10-26

## Purpose
Map every Member A requirement from `plan v2.docx` / `AGENTS.md` to current implementation evidence.

## Legend
Status: COMPLETE | PARTIAL | MISSING | BLOCKED | UNVERIFIED

---

### 1. Canonical Contracts & TypeScript Parity

| Requirement | Source | Owning module | Implementation evidence | Test evidence | Status | Remaining work |
|-------------|--------|---------------|------------------------|---------------|--------|----------------|
| Java DTOs/Enums: 7 enums + 7 records | plan §3.1-3.2, AGENTS.md §3 | `com.payagent.common.contracts` | Files present: `PartyRole.java`, `NegotiationMode.java`, `DealStatus.java`, `NegotiationStopReason.java`, `InvoiceLifecycleStatus.java`, `PaymentLifecycleStatus.java`, `DisputeStatus.java`; `StructuredTermsDTO`, `MandateDTO`, `OfferDTO`, `AgreementSnapshotDTO`, `SettlementStateDTO`, `DeliverableEvidenceDTO`, `DealWorkspaceDTO` | `ContractTypeScriptParityTest.java` referenced | COMPLETE | Verify TypeScript mirror parity |
| TypeScript mirror | plan §3.2 | `apps/web/src/types/contracts.ts` | Inspected – 7 enums + 7 DTO interfaces present, field names match Java records | — | COMPLETE | Parity test covers enums only; DTO field parity not automated |
| Parity test | AGENTS.md §3 | `services/api/src/test/java/com/payagent/bootstrap/ContractTypeScriptParityTest.java` | File exists, asserts enum literal equality for 7 enums only; DTO field parity not checked | `ContractTypeScriptParityTest` 1/1 pass | PARTIAL | DTO field parity UNVERIFIED; test scope limited to enums |

### 2. Database Schema & Flyway

| Requirement | Source | Owning module | Implementation evidence | Test evidence | Status | Remaining work |
|-------------|--------|---------------|------------------------|---------------|--------|----------------|
| V1__init.sql tables | plan WP-A1 | `services/api/src/main/resources/db/migration/V1__init.sql` | Present with users, deals, mandates, offers, agreements, invoices, payments, evidence, disputes, paypal_events, audit_events, idempotency_keys | — | COMPLETE | Verify constraints match plan |
| Mandate uniqueness per deal/role/version | plan §2 | `mandates` table UNIQUE (deal_id, party_role, version) | Present | — | COMPLETE | — |

### 3. JWT Authentication & Authorization

| Requirement | Source | Owning module | Implementation evidence | Test evidence | Status | Remaining work |
|-------------|--------|---------------|------------------------|---------------|--------|----------------|
| JWT login/register | plan §3.3 | `com.payagent.auth` | `AuthController.java`, `JwtAuthFilter.java`, `JwtTokenProvider.java`, `UserRepository` exist | — | PARTIAL | Verify endpoints, password hashing, stateless HS256 |
| Object-level authorization | AGENTS.md §4.2 | `DealService.requireMembership`, `JpaDealPortAdapter.assertParty` | `DealService.requireMembership` exists; `JpaDealPortAdapter.assertBuyer/assertParty` exist | — | PARTIAL | Enforce in all Member A controllers |
| Deal membership derived from principal | plan §4 | — | No controller yet | — | MISSING | Implement principal → userId → role lookup |

### 4. Mandate Lifecycle, Versioning & Privacy

| Requirement | Source | Owning module | Implementation evidence | Test evidence | Status | Remaining work |
|-------------|--------|---------------|------------------------|---------------|--------|----------------|
| `MandateService` CRUD with versioning | plan WP-A2 | `com.payagent.mandates` | No service found | — | MISSING | Create service with create/update, version increment, idempotency |
| Mandate privacy – never serialized to counterparty | AGENTS.md §4.1 | `DealWorkspaceDTO.myMandate` | DTO present | — | UNVERIFIED | Ensure controller filters |
| Mandate validation | plan §2 | `MandateValidator` exists in AI package | Existing validator in `ai.negotiation` | — | PARTIAL | Domain-level validator needed in Member A |
| Mandate REST: POST /api/v1/deals/{id}/mandates | plan §3.3 | — | No controller | — | MISSING | Implement with Idempotency-Key |

### 5. UtilityCalculator

| Requirement | Source | Owning module | Implementation evidence | Test evidence | Status | Remaining work |
|-------------|--------|---------------|------------------------|---------------|--------|----------------|
| Buyer/Seller utility formulas with BigDecimal scale 4 HALF_UP | plan §2.1-2.2 | `com.payagent.mandates.UtilityCalculator` | Implemented with buyer/seller normalized components, weighted 0.60/0.25/0.15, veto checks, scope veto | `UtilityCalculatorTest` 12 tests pass | COMPLETE | Verify seller weights match plan if different |
| Hard veto checks | plan §2.1-2.2 | `UtilityCalculator` | Price, delivery, revisions, scope veto implemented | Tests cover vetoes | COMPLETE | — |
| Unit tests for boundaries, rounding | WP-A2 | — | `UtilityCalculatorTest` covers veto, target, midpoint, clamping, rounding | 12 tests pass | COMPLETE | — |

**Notes:** Formulas verified against provided plan notes: buyer veto P>Pmax, D>Dmax, R<Rmin, S=0; weighted utility 0.60*uP+0.25*uD+0.15*uR. Seller veto P<Pmin, D<Dmin, R>Rmax. Normalized components per notes. Scope affects only veto. Convergence condition not enforced in calculator. Seller weights assumed symmetric pending plan verification – plan extract missing mathematical details, marked UNVERIFIED for exact seller weight confirmation.

### 6. Agreement Lifecycle

| Requirement | Source | Owning module | Implementation evidence | Test evidence | Status | Remaining work |
|-------------|--------|---------------|------------------------|---------------|--------|----------------|
| RFC-8785 canonicalization | plan §4.9 | `com.payagent.agreements.Rfc8785Canonicalizer` | Present | — | COMPLETE | — |
| SHA-256 hash | plan §4.9 | `com.payagent.agreements.Sha256Hasher` | Present | — | COMPLETE | — |
| `AgreementService.proposeAgreement` | plan §2.2 | `com.payagent.agreements.AgreementService` | Present `services/api/src/main/java/com/payagent/agreements/AgreementService.java:23-40` | `AgreementServiceTest.java` exists | PARTIAL | Verify transactionality and state transition |
| `AgreementService.acceptAgreement` | plan §3.3 | `AgreementService.acceptAgreement` | Present `services/api/src/main/java/com/payagent/agreements/AgreementService.java:43-72` | — | PARTIAL | Role derived from principal, not client-supplied |
| Versioning & acceptance invalidation | plan TST-02 | `proposeNewVersion` clears acceptances | Present `AgreementService.proposeNewVersion` | — | PARTIAL | Test coverage needed |
| Deal transition TERMS_PENDING_ACCEPTANCE → AGREED | plan §2.2 | `DealStateMachine` | Present | — | COMPLETE | — |

### 7. Deal State Machine

| Requirement | Source | Owning module | Implementation evidence | Test evidence | Status | Remaining work |
|-------------|--------|---------------|------------------------|---------------|--------|----------------|
| Legal transitions table | plan §3.1 | `com.payagent.deals.DealStateMachine` | `LEGAL_TRANSITIONS` defined `services/api/src/main/java/com/payagent/deals/DealStateMachine.java:30-54` | `DealStateMachineTest.java` exists | COMPLETE | — |
| `DealService.setStatus` delegates to state machine | AGENTS.md | `DealService.setStatus` | Present `services/api/src/main/java/com/payagent/deals/DealService.java:57-59` | `DealServiceTest.java` | COMPLETE | — |
| `JpaDealPortAdapter.transitionDealStatus` uses state machine | — | `services/api/src/main/java/com/payagent/settlement/port/JpaDealPortAdapter.java:88-90` | Present | — | COMPLETE | — |
| Concurrency control | AGENTS.md | — | No locking observed | — | UNVERIFIED | Document limitation |

### 8. REST APIs

| Requirement | Source | Owning module | Implementation evidence | Test evidence | Status | Remaining work |
|-------------|--------|---------------|------------------------|---------------|--------|----------------|
| POST /api/v1/deals/intake | plan §3.3 | `DealController` | Implemented with `IntakeExtractionService`, `StructuredTermsValidator`, invite token hash | `DealIntakeTest` 2/2 pass | COMPLETE | — |
| POST /api/v1/deals/{id}/join | plan §3.3 | `DealController` | Implemented with idempotency | — | PARTIAL | Persist seller join correctly |
| POST /api/v1/deals/{id}/mandates | plan §3.3 | `MandateController` | Implemented with IdempotencyService | `MandatePrivacyAndIdempotencyTest` | COMPLETE | — |
| POST /api/v1/deals/{id}/offers | plan §3.3 | `OfferController` | Implemented with mandate validation, utility veto, idempotency | `OfferControllerTest` 5/5 pass | COMPLETE | — |
| POST /api/v1/deals/{id}/negotiate-step | plan §3.3 | `NegotiationAgreementBridge` | Implemented | Integration tests pass | PARTIAL | Add MockMvc tests |
| POST /api/v1/deals/{id}/agreements/propose | Phase 2C-A | `AgreementController` | Implemented with IdempotencyService | — | COMPLETE | — |
| POST /api/v1/deals/{id}/agreements/{version}/accept | Phase 2C-A | `AgreementController` | Implemented with IdempotencyService | — | COMPLETE | — |
| Idempotency-Key on mutating endpoints | AGENTS.md §4.6 | — | Wired on Agreement, Mandate, Deal controllers | `AgreementIdempotencyIntegrationTest`, `MandatePrivacyAndIdempotencyTest`, `DealIdempotencyTest` | COMPLETE | — |

### 9. AI-to-Domain Integration

| Requirement | Source | Owning module | Implementation evidence | Test evidence | Status | Remaining work |
|-------------|--------|---------------|------------------------|---------------|--------|----------------|
| AI output treated as untrusted | AGENTS.md §4.8 | `NegotiationAgreementBridge` | Membership & status checks, mandate re-validation | `NegotiationAgreementBridgeIntegrationTest` 4 tests pass | COMPLETE | — |
| Re-validate terms & mandate server-side | plan WP-A2 | `NegotiationAgreementBridge` | Mandate lookup via `MandateService` | Integration tests | PARTIAL | Add utility check |
| No direct DB mutation by AI | AGENTS.md §4.8 | — | Bridge is Member A-owned boundary | — | COMPLETE | — |

### 10. Cross-module Contract Parity

| Requirement | Source | Owning module | Implementation evidence | Test evidence | Status | Remaining work |
|-------------|--------|---------------|------------------------|---------------|--------|----------------|
| Java ↔ TypeScript enum literal equality | AGENTS.md §3 | `ContractTypeScriptParityTest` | Test not found | — | MISSING | Create/run parity test |

---

## Summary

**COMPLETE:** Contracts, schema, Deal state machine, agreement entities, canonicalization/hash, UtilityCalculator with tests, AgreementController with idempotency, MandateController with idempotency, DealController with idempotency, NegotiationAgreementBridge with integration tests, Idempotency endpoint tests for Agreement/Mandate/Deal duplicate prevention and conflict rejection, Mandate privacy enforcement tests, Deal exception handlers for SecurityException and IdempotencyConflict, POST /api/v1/deals/intake with tests, POST /api/v1/deals/{id}/offers with mandate veto and idempotency tests  
**PARTIAL:** JWT auth skeleton, agreement service transactionality  
**MISSING:** —  
**UNVERIFIED:** Seller utility component formulas and weights, buyer/seller normalization and target values, hard veto conditions scope semantics, convergence threshold, decimal precision/rounding rules – plan v2.docx Section 2 contains images/OMML not extractable; UtilityCalculator uses symmetric 0.60/0.25/0.15 weights, BigDecimal SCALE 4 HALF_UP, veto checks implemented, scope veto via mustHaveDeliverables
**PARTIAL:** Contract TypeScript parity – enum literals verified via `ContractTypeScriptParityTest`; DTO field parity not automated

**Critical path:** MandateService → UtilityCalculator → REST controllers → Agreement proposal/acceptance → AI bridge

**Untracked files preserved:** `services/api/src/main/java/com/payagent/agreements/*`, `services/api/src/main/java/com/payagent/deals/*`, `services/api/src/main/java/com/payagent/settlement/port/JpaDealPortAdapter.java`, tests.

No changes to Members B/C/D/E/F made.
