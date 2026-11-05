# WP-A2 Detailed Sub-Plan — Backend Domain Core

**Work Package:** WP-A2  
**Owner:** Member A  
**Source of Truth:** `plan v2.docx` (extracted to `docs/_plan_extract.txt`), `AGENTS.md`, `docs/ARCHITECTURE.md`  
**Status:** Planning phase — implementation freeze until approval  
**Last Updated:** 2026-10-23

## 1. Scope, Objectives, Non-Goals

### Objectives
Implement backend domain core for mandates, utility calculation, and agreement integrity as defined in plan §2 and §3.

Specifically:
- Deterministic `UtilityCalculator` with exact multi-attribute formulas from plan §2
- Versioned mandate persistence, validation, privacy, and retrieval
- Agreement proposal, RFC-8785 canonicalization, SHA-256 hashing, dual acceptance, version invalidation
- Integration points with existing contracts, auth, idempotency, and deal state machine

### Explicit Non-Goals
- PayPal HTTP clients, settlement workflows — Member B
- Spring AI prompts / counteroffer generation — Member C
- Next.js UI — Member D
- Evidence storage, disputes, WebSocket realtime — Member E
- No changes to canonical DTOs/Enums unless required by source plan

## 2. Source-Plan Traceability

| Req ID | Source | Requirement | Notes |
|--------|--------|-------------|-------|
| WP-A2-01 | plan v2.docx §2.1 | Buyer utility function with weights wP=0.60,wD=0.25,wR=0.15, hard vetoes, clamp | Formulas extracted via `_plan_extract.txt` |
| WP-A2-02 | plan v2.docx §2.2 | Seller utility function, hard vetoes, convergence threshold 0.70 | |
| WP-A2-03 | AGENTS.md §4.9 | Agreement hash = SHA-256 of RFC-8785 canonical JSON of canonicalTerms | |
| WP-A2-04 | AGENTS.md §4.1 | Mandate privacy — never serialize counterparty mandate | |
| WP-A2-05 | AGENTS.md §4.6 | All mutating endpoints require Idempotency-Key | |
| WP-A2-06 | AGENTS.md §4.2 | Object-level authorization, 403 for cross-account | |
| WP-A2-07 | ARCHITECTURE.md §2.1 | POST /deals/{id}/mandates, GET /deals/{id}/mandates/me | |
| WP-A2-08 | `V1__init.sql` | mandates table unique (deal_id,party_role,version) | Schema exists |

## 3. Requirements and Invariants

### UtilityCalculator
- BigDecimal, scale 4, RoundingMode.HALF_UP
- Price, delivery, revisions component scores clamped [0,1]
- Hard veto → utility = -1
- Zero denominator handling
- Deterministic, testable without Spring

### Mandates
- JPA entity maps to `mandates` table columns
- Validate version ≥1, expiresAt > now
- Buyer: limitPrice ≥ targetPrice; Seller: limitPrice ≤ targetPrice
- Uniqueness enforced at DB level
- Authenticated user identity from JWT, never trust client-supplied user ID
- `getMyMandate` verifies deal membership
- Never expose counterparty mandate
- Idempotency-Key required for POST, 409 on key reuse with different payload, 422 for invalid requests

### Agreements
- Canonicalization via RFC-8785 JSON Canonicalization Scheme
- SHA-256 hex digest 64 chars
- New version on terms change → clear prior acceptances
- Accept only if version and hash match
- Acceptance by correct party only
- Deal transitions to AGREED only after both parties accept identical version/hash

## 4. Architecture & Component Responsibilities

```
com.payagent.mandates
  UtilityCalculator
  Mandate Entity/Repository/Service/Controller

com.payagent.agreements
  Agreement Entity/Repository/Service
  Rfc8785Canonicalizer
  Sha256Hasher
```

No changes to `common.contracts`.

## 5. API & Data-Contract Requirements

Existing DTOs must be used verbatim:
- `MandateDTO`, `StructuredTermsDTO`, `AgreementSnapshotDTO`

Endpoints:
- POST `/api/v1/deals/{id}/mandates` → 201 `MandateDTO`
- GET `/api/v1/deals/{id}/mandates/me` → 200 `MandateDTO`

Error semantics per plan: 422 Unprocessable Entity for invalid mandate, 409 Conflict for idempotency key reuse with different payload, 403 Forbidden for cross-account access.

## 6. Database & Migration Implications

`V1__init.sql` already defines `mandates` and `agreements`. No schema changes planned. If RFC-8785 canonicalization requires new column, a new Flyway migration must be proposed and approved.

## 7. Authentication, Authorization, Privacy

- JWT auth via existing `JwtAuthFilter`
- Object-level deal guard: user must be buyer or seller on deal
- MandateDTO never serialized to counterparty
- WebSocket masking required per ARCHITECTURE.md §2.2

## 8. Idempotency, Concurrency, Transactions

- Use existing `idempotency_keys` table
- Mutating mandate creation requires Idempotency-Key header
- Transaction boundaries: mandate create + idempotency record atomic
- Agreement proposal and acceptance must be transactional

## 9. Canonicalization & Hash-Integrity

- Requirement: RFC-8785 JSON Canonicalization Scheme
- Current implementation uses Jackson sorted keys – **not verified as RFC 8785 compliant**
- Unresolved: Which maintained library implements RFC 8785 for Java? Requires approval before integration.
- Hash input: canonical UTF-8 JSON bytes → SHA-256
- Acceptance must verify exact hash

## 10. Testing & Regression Strategy

- Unit tests for `UtilityCalculator` covering buyer/seller, veto, clamping, edge cases
- Integration tests for mandate create/retrieve with auth and privacy
- Integration tests for agreement propose/accept with hash verification
- Negative tests for cross-account access, idempotency conflicts
- Existing tests must remain green: `ContractTypeScriptParityTest`, `ContractFixtureDeserializationTest`, auth tests
- Run: `./mvnw -B test`

## 11. Dependencies & Execution Order

1. Verify auth and deal guard
2. UtilityCalculator
3. Mandate entity/repository/service/controller
4. Agreement canonicalizer/hasher
5. Agreement service
6. Integration tests
7. Security/privacy review

## 12. Risks, Unresolved Questions, Decisions Requiring Approval

- **Unresolved:** Exact RFC 8785 implementation library and conformance vectors
- **Unresolved:** Serialization strategy for `mustHaveDeliverables`/`forbiddenTerms` JSONB – current simple string builder is insufficient
- **Unresolved:** How to derive `PartyRole` from JWT + deal membership without DealService
- **Unresolved:** Idempotency mechanism details – existing implementation incomplete
- **Risk:** Current untracked skeleton code diverges from plan; may need to be discarded
- **Decision needed:** Whether to retain or discard existing untracked WP-A2 files before approved phase start
- **Risk:** Phase A1 baseline verification requires working tree free of production changes while planning artifacts exist as untracked files; process must distinguish planning docs from implementation changes

## 13. Acceptance Criteria & Definition of Done

- UtilityCalculator formulas match plan §2 exactly, verified by unit tests
- Mandate endpoints enforce validation, privacy, auth, idempotency
- Agreement proposal produces deterministic hash, versioning works, acceptance clearing works
- All existing tests pass
- No mandate data leaks to counterparty
- `git diff --check` passes, no unintended changes

**Definition of Done:** Measurable acceptance criteria met, security review passed, phase report approved.
