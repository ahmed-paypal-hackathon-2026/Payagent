# WP-A2 Decision Proposal

**Date:** 2026-10-24  
**Branch:** backend/wp-a2 @ ca0c9f7  
**Status:** Decision-preparation only — no implementation

## 1. RFC 8785 Canonicalization Library

**Repository evidence**
- `services/api/pom.xml`: Spring Boot 3.3.5, Java 21, dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `jjwt-api` 0.12.6, `jjwt-jackson`, `flyway-core`. No RFC 8785 library.
- Untracked `services/api/src/main/java/com/payagent/agreements/Rfc8785Canonicalizer.java` uses `ObjectMapper` with `ORDER_MAP_ENTRIES_BY_KEYS`. No RFC 8785-specific processing.
- `AGENTS.md §4.9`: `termsHashSha256` = SHA-256 of RFC-8785 canonical JSON of `canonicalTerms`.

**Strongest option supported by evidence**
Use existing Jackson infrastructure with explicit documented deviation and require approval for true RFC 8785 compliance. No library is present and cannot be added without build change.

**Alternatives**
- Option A: Keep current Jackson sorted-keys approximation, document deviation, accept risk of hash mismatch vs spec.
- Option B: Add external RFC 8785 library e.g., `com.github...` or `org.bouncycastle` JSON canonicalization. Requires dependency change, verification against conformance vectors.
- Option C: Defer agreement hashing until library approved.

**Trade-offs**
- A is fastest but violates source requirement.
- B provides compliance but adds dependency and requires verification.
- C delays WP-A2.

**Security/correctness/deadline risks**
- Non-compliant canonicalization → different hashes across implementations → acceptance failures, audit issues.
- Deadline risk if library integration requires testing.

**What must be verified**
- Existence of maintained Java RFC 8785 library compatible with Spring Boot 3.3.5/Java 21.
- Conformance test vectors pass.
- Hash output matches spec for edge cases: Unicode normalization, number formatting, empty structures.

**Recommendation**
Do not claim RFC 8785 compliance. Propose Option B with explicit library selection and conformance testing before Phase A5.

## 2. Idempotency Implementation

**Repository evidence**
- Schema `V1__init.sql` lines 135-143: `idempotency_keys` table with PK `(idempotency_key, route)`, columns `deal_id UUID, request_hash CHAR(64), response_status INT, response_body JSONB`.
- No Java service/repository using table in tracked code.
- Untracked `MandateController.java` reads `Idempotency-Key` header but contains placeholder comment only.
- `AGENTS.md §4.6`: All mutating endpoints require Idempotency-Key.

**Strongest option**
Implement idempotency service using existing schema with Spring `@Transactional` check-then-insert pattern:
1. Compute request hash (SHA-256 of canonical request body + route)
2. `SELECT` by key+route; if found and hash matches → return stored response
3. If found with different hash → 409 Conflict
4. Otherwise proceed, persist response on success

**Alternatives**
- Rely on database unique constraint only → no replay semantics, no response caching.
- Use external idempotency library.

**Trade-offs**
- Service implementation is straightforward and matches schema.
- Requires careful transaction isolation to avoid race.

**Risks**
- Missing implementation → duplicate mandate creation on retry.
- Incorrect hash computation → false conflicts.

**Must verify**
- Exact request hash algorithm expected by team.
- Transaction isolation level and locking strategy.
- Response body serialization format for JSONB column.

## 3. Deal Membership and Party Role Resolution

**Repository evidence**
- `JwtAuthFilter.java` lines 50-62: extracts `userId` from JWT, stores `request.setAttribute("userId", userId)`. SecurityContext populated.
- Schema `V1__init.sql` lines 10-19: `deals` table with `buyer_user_id UUID NOT NULL REFERENCES users(id)`, `seller_user_id UUID REFERENCES users(id)`.
- No Deal entity/repository in tracked code.
- Untracked `MandateController.java` line 32 hardcodes `PartyRole role = PartyRole.BUYER; // TODO: resolve from JWT + deal participants`.

**Strongest option**
Implement minimal deal membership check via direct JPA query on `deals` table without full DealService:
- Load deal by ID, compare `buyer_user_id` and `seller_user_id` to authenticated `userId` from request attribute.
- Derive `PartyRole` from which side matches.
- Reject if no match → 403 Forbidden.

**Alternatives**
- Create full `DealService` and entity — larger scope.
- Trust client-supplied role — violates `AGENTS.md §4.2` object-level authorization.

**Trade-offs**
- Direct query is minimal and sufficient for WP-A2.
- Full DealService adds scope creep.

**Risks**
- No deal existence check → 404.
- Race conditions if deal changes between check and write.

**Must verify**
- Authenticated `userId` is reliably available in controller via request attribute or SecurityContext.
- Deal table columns are indexed for membership lookup.

## 4. Safe JSONB Mapping for Mandate Lists

**Repository evidence**
- Schema `V1__init.sql` lines 33-34: `must_have_deliverables JSONB NOT NULL DEFAULT '[]'`, `forbidden_terms JSONB NOT NULL DEFAULT '[]'`.
- Untracked `Mandate.java` maps columns as `String mustHaveDeliverablesJson`, `String forbiddenTermsJson` with manual string builder.
- DTO `MandateDTO` defines `List<String> mustHaveDeliverables`, `List<String> forbiddenTerms`.

**Strongest option**
Use Hibernate JSON mapping with Jackson:
- Add `@JdbcTypeCode(SqlTypes.JSON)` with `@Convert` or Hibernate Types.
- Map entity fields directly to `List<String>`.
- Requires no schema change.

**Alternatives**
- Keep String column with manual JSON serialization — error prone.
- Change schema to text — loses JSONB queryability.

**Trade-offs**
- Hibernate JSON mapping is standard, type-safe.
- Requires dependency on `hibernate-types-52` or Spring Boot 3.2+ native support.

**Risks**
- Manual builder risks invalid JSON, injection.
- Deserialization errors at runtime.

**Must verify**
- Hibernate version supports JSON mapping for PostgreSQL.
- Jackson module for Java Time present.

## 5. Retain vs Discard Untracked WP-A2 Skeleton Files

**Repository evidence**
- Untracked files listed in previous report.
- Files contain partial implementation with known gaps: placeholder role, no idempotency, Jackson-only canonicalization, manual JSON builder.
- No commits.

**Strongest option**
Retain files as reference but mark them as pre-approval prototypes. Do not merge into approved work. Require fresh implementation under approved plan.

**Alternatives**
- Discard files to avoid confusion — loses UtilityCalculator work which is test-passed.
- Keep and refactor — risk of carrying over incorrect assumptions.

**Trade-offs**
- Retain preserves 13 passing UtilityCalculator tests.
- Discard ensures clean start.

**Recommendation**
Retain `UtilityCalculator.java` and its tests as they match source formulas and have verified tests. Discard or quarantine other skeleton files until decisions resolved.

## Phase Dependency Graph Review

**Current A1→A2→A3→A4→A5→A6→A7**

**Genuinely required dependencies**
- A1 foundation verification required before any code changes.
- A4 requires A3 model.
- A6 requires A5 canonicalization decision.
- A7 requires A4 and A6.

**Convenient ordering, could be parallel**
- A2 UtilityCalculator is independent of A3 Mandate model.
- A5 canonicalization is independent of A3/A4.
- A2 and A3 could run in parallel after A1.

**Recommended graph**
- A1 → { A2, A3, A5 }
- A3 → A4
- A5 → A6
- { A4, A6, A2 } → A7

This reduces critical path while preserving review gates.

---

**Files created:** `docs/WP-A2-DECISION-PROPOSAL.md`  
**Files modified:** None  
**Tests run:** None
