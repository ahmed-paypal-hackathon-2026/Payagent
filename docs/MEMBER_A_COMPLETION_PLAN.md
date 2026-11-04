# Member A Completion Plan

**Workspace:** `C:\Users\mona mohamed\Downloads\Payagent-integration-20261025\repo`
**Branch:** `integration/member-a-reconciliation`
**HEAD:** `bc3fc4acdad15b68b5e047c6bccb3b4cd08f56f5`
**Owner:** Member A
**Last updated:** 2026-10-26

## Objective
Complete all Member A domain requirements with production quality, tests, and verification.

## Dependencies
- No changes to Members B/C/D/E/F unless integration adapter required.
- Preserve untracked Member A files.
- No commits/pushes.

## Phase 1 – Foundation & Contracts

### 1.1 Verify contracts parity
- Inspect `apps/web/src/types/contracts.ts` mirror.
- Run `ContractTypeScriptParityTest` or create minimal parity test.
- **File:** `services/api/src/test/java/com/payagent/bootstrap/ContractTypeScriptParityTest.java`
- **Acceptance:** All enums/DTO fields match 1:1.

### 1.2 JWT Auth hardening
- Verify `AuthController` endpoints `/api/v1/auth/login` and `/register`.
- Ensure `JwtAuthFilter` resolves principal to `User`.
- **File:** `com/payagent/auth/*`
- **Acceptance:** `AuthAndSchemaTest` passes.

## Phase 2 – Mandate Service & Privacy

### 2.1 Implement `MandateService`
- Create `services/api/src/main/java/com/payagent/mandates/MandateService.java`
- Methods:
  - `createMandate(UUID dealId, UUID userId, MandateCreateRequest req, String idempotencyKey)`
  - `updateMandate(UUID dealId, UUID userId, int version, ...)`
  - `getMandate(UUID dealId, UUID userId)` – scoped to owner only.
- Enforce version increment, uniqueness constraint.
- Privacy: never return counterparty mandate in `DealWorkspaceDTO`.
- **Tests:** `MandateServiceTest` – valid create, version bump, access control, privacy masking.

### 2.2 Mandate REST controller
- `POST /api/v1/deals/{id}/mandates`
- Request: `MandateCreateRequest`, `Idempotency-Key` header.
- Auth: JWT, membership check.
- Response: `MandateDTO`.
- **File:** `com/payagent/mandates/MandateController.java`
- **Acceptance:** 422 on mandate violation, 403 on cross-account.

## Phase 3 – UtilityCalculator

### 3.1 Implement `UtilityCalculator`
- Create `services/api/src/main/java/com/payagent/mandates/UtilityCalculator.java`
- Formulas from plan §2.1-2.2:
  - Buyer utility components: price, delivery days, revisions, deliverables inclusion.
  - Seller utility components.
  - Hard veto checks.
  - BigDecimal scale 4, `RoundingMode.HALF_UP`.
- **Blocker:** Exact formulas not fully extracted. Use documented structure and mark UNVERIFIED until source confirmed.
- **Tests:** `UtilityCalculatorTest` – normal values, boundaries, rounding, veto.

## Phase 4 – Agreement Lifecycle Hardening

### 4.1 Harden `AgreementService`
- Ensure `proposeAgreement` validates deal status `ACTIVE_NEGOTIATION`.
- Ensure `acceptAgreement` derives party role from principal, never trusts client.
- Ensure stale version rejection and hash match.
- Transactional consistency with state machine.
- **File:** `com/payagent/agreements/AgreementService.java`
- **Tests:** `AgreementServiceTest` – version bump clears acceptances, hash invalidation.

### 4.2 Agreement REST controllers
- `POST /api/v1/deals/{id}/agreements/propose`
  - Auth, membership, status check.
  - Idempotency-Key.
  - Request: `StructuredTermsDTO`.
  - Transition to `TERMS_PENDING_ACCEPTANCE`.
- `POST /api/v1/deals/{id}/agreements/{version}/accept`
  - Auth, membership.
  - Derive role from principal via `DealService.requireMembership`.
  - Validate hash, latest version.
  - Transition to `AGREED` when both accepted.
- **File:** `com/payagent/agreements/AgreementController.java`
- **Acceptance:** 409 on hash mismatch, 403 on non-participant.

## Phase 5 – Deal REST APIs

### 5.1 Deal intake & join
- `POST /api/v1/deals/intake` – create deal + buyer mandate.
- `POST /api/v1/deals/{id}/join` – seller joins via invite token.
- **File:** `com/payagent/deals/DealController.java`
- **Acceptance:** Correct status transitions, invite token validation.

### 5.2 Offers
- `POST /api/v1/deals/{id}/offers`
- Validate against caller's mandate, compute utility, store `OfferDTO`.
- Idempotency-Key.
- **Acceptance:** 422 if violates mandate.

## Phase 6 – AI-to-Domain Integration Bridge

### 6.1 NegotiationAgreementBridge
- Create `com/payagent/deals/NegotiationAgreementBridge.java`
- Input: `ProposedOfferDTO` from Member C (untrusted).
- Steps:
  1. Load deal, verify membership.
  2. Verify status `ACTIVE_NEGOTIGRATION`.
  3. Re-validate terms with `StructuredTermsValidator`.
  4. Re-validate against authoritative `MandateService`.
  5. If `action == ACCEPT` and `requiresHumanApproval == false`, propose agreement.
- Do not trust AI validation flags.
- **Acceptance:** Invalid terms rejected before persistence.

### 6.2 Optional public endpoint
- `POST /api/v1/deals/{id}/negotiate/decision` – if plan requires public API.
- Otherwise keep internal service boundary.

## Phase 7 – Idempotency & Authorization

- Apply `IdempotencyService` to all mutating Member A endpoints.
- Ensure all responses include proper error codes.
- Object-level auth on every deal/mandate/agreement access.

## Phase 8 – Tests & Verification

### 8.1 Unit tests
- `UtilityCalculatorTest`
- `MandateServiceTest`
- `AgreementServiceTest` – extended
- `DealStateMachineTest` – already exists, verify coverage

### 8.2 Integration tests
- Valid negotiation → agreement proposal → dual acceptance → `AGREED`
- Invalid terms rejection
- Non-participant blocked
- Stale hash rejected
- Idempotency replay
- Mandate privacy masking

### 8.3 Run commands
```bash
cd services/api
./mvnw -B test -Dtest=AuthAndSchemaTest,UtilityCalculatorTest,MandateServiceTest,AgreementServiceTest,DealStateMachineTest
./mvnw -B test
```
Capture exit codes.

## Implementation Order by Risk

1. MandateService + controller – blocks utility validation
2. UtilityCalculator – blocks AI integration
3. Agreement controllers – blocks acceptance flow
4. Deal intake/join – blocks end-to-end
5. AI bridge – final integration
6. Tests & verification

## Open Questions
- Exact UtilityCalculator formulas – need plan §2 exact math.
- Should `ProposedOfferDTO` be public API type or internal?
- Idempotency key scope for negotiation decision vs agreement proposal.

## Success Criteria
- All Member A requirements from plan are COMPLETE or documented as BLOCKED with evidence.
- `./mvnw -B test -Dtest=UtilityCalculatorTest,NegotiationAgreementBridgeIntegrationTest,ContractTypeScriptParityTest,AgreementIdempotencyIntegrationTest,MandatePrivacyAndIdempotencyTest,DealIdempotencyTest` passes with zero failures.
- No changes to Members B/C/D/E/F.
- `git status --short` unchanged except new tracked files created per plan.
- Gap analysis updated to reflect final status.

## Notes
- Preserve untracked files.
- Do not commit.
- Document concurrency limitation: DealStateMachine has no locking.

## Final Verification 2026-10-26
- Focused Member A suite command:
  `cd services/api && cmd /c "mvnw -B test -Dtest=UtilityCalculatorTest,NegotiationAgreementBridgeIntegrationTest,ContractTypeScriptParityTest,AgreementIdempotencyIntegrationTest,MandatePrivacyAndIdempotencyTest,DealIdempotencyTest,DealIntakeTest,OfferControllerTest"`
- Results: Tests run: 33, Failures: 0, Errors: 0, Skipped: 0, Exit code 0, BUILD SUCCESS
  - UtilityCalculatorTest: 12 tests run, 0 failures, 0 errors
  - NegotiationAgreementBridgeIntegrationTest: 4 tests run, 0 failures, 0 errors
  - ContractTypeScriptParityTest: 1 test run, 0 failures, 0 errors – enum literals only; DTO field parity not covered
  - AgreementIdempotencyIntegrationTest: 2 tests run, 0 failures
  - MandatePrivacyAndIdempotencyTest: 4 tests run, 0 failures
  - DealIdempotencyTest: 3 tests run, 0 failures
  - DealIntakeTest: 2 tests run, 0 failures
  - OfferControllerTest: 5 tests run, 0 failures
- Full API suite: `mvnw -B test` – Tests run: 214, Failures: 0, Errors: 46, Skipped: 5, BUILD FAILURE
  - Errors are environment/toolchain: Mockito/Byte Buddy incompatibility with Java 25 for Member B PayPal tests (PayPalInvoiceService, PayPalAuthClient, PayPalWebhookVerifier, SettlementService mocking). Member A tests unaffected.
- Contract parity: Enum literals verified; DTO field parity not automated – PARTIAL
- Utility formulas: Buyer component formulas and weights confirmed from implementation: price 0.60 inverse normalized, delivery 0.25 inverse, revisions 0.15 direct, BigDecimal SCALE 4 HALF_UP, veto checks for price>limit, delivery>limit, revisions<limit, scope mustHaveDeliverables. Seller weights assumed symmetric 0.60/0.25/0.15 pending authoritative plan v2.docx Section 2 extraction – UNVERIFIED for seller exact formulas, normalization targets, convergence threshold, veto scope semantics.
- Intake/offers endpoints verified: POST /api/v1/deals/intake and POST /api/v1/deals/{id}/offers implemented with membership, active state, mandate validation, utility veto, Idempotency-Key.
- git status --short confirms untracked Member A files preserved, no changes to Members B/C/D/E/F. Branch integration/member-a-reconciliation HEAD bc3fc4acdad15b68b5e047c6bccb3b4cd08f56f5 unchanged.
