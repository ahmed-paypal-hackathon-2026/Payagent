# WP-A2 Decision-Preparation Report

**Date:** 2026-10-24  
**Branch:** backend/wp-a2 @ ca0c9f7  
**Status:** Planning review only — no implementation

## 1. Repository State

- Branch: backend/wp-a2, HEAD ca0c9f7
- git status --short shows untracked planning docs and untracked WP-A2 skeleton files
- No tracked changes
- No commits made in this session

## 2. RFC 8785 Decision

**Evidence:**
- `services/api/pom.xml` dependencies: Spring Boot 3.3.5, Java 21, Jackson via spring-boot-starter-web, jjwt-jackson. No RFC 8785 library present.
- `docs/WP-A2-SUBPLAN.md` §9 notes current implementation uses Jackson sorted keys, not verified RFC 8785 compliant.
- `services/api/src/main/java/com/payagent/agreements/Rfc8785Canonicalizer.java` (untracked) uses `ObjectMapper` with `ORDER_MAP_ENTRIES_BY_KEYS`. No RFC 8785-specific processing.

**Options:**
1. Use existing Jackson approximation with documented deviation — risks hash mismatch with spec.
2. Integrate maintained Java RFC 8785 library — requires dependency approval and verification.
3. Defer agreement hashing until library approved.

**Trade-offs:** Proven compliance requires library with conformance vectors. Jackson ordering alone does not guarantee RFC 8785 lexical form, e.g., Unicode normalization, number formatting, empty array/object representation.

**Recommendation:** Do not claim RFC 8785 compliance without library. Record unresolved.

**Confidence:** High evidence of missing library.

## 3. Idempotency

**Evidence:**
- Schema: `V1__init.sql` lines 135-143 defines `idempotency_keys` with PK `(idempotency_key, route)`, columns `deal_id`, `request_hash`, `response_status`, `response_body JSONB`.
- Code search: No service/repository using table found in tracked code. Untracked `MandateController.java` references Idempotency-Key header but contains only placeholder comment.
- `AGENTS.md §4.6` requires Idempotency-Key for all mutating endpoints.

**Current behavior:** Table exists, no enforcement code.

**Options:**
1. Implement idempotency interceptor/service using existing schema.
2. Use Spring's built-in idempotent infrastructure with custom store.
3. Defer mandate creation until idempotency service approved.

**Recommendation:** Idempotency service must be implemented before Phase A4. Unresolved.

## 4. Deal Authorization and Party Roles

**Evidence:**
- `JwtAuthFilter.java` sets `request.setAttribute("userId", userId)` and `userEmail`. SecurityContextHolder holds Authentication.
- No Deal entity or DealRepository exists in tracked code.
- `services/api/src/main/java/com/payagent/mandates/MandateController.java` line 32 hardcodes `PartyRole role = PartyRole.BUYER; // TODO: resolve from JWT + deal participants`.
- `MandateService.createMandate` receives `partyRole` as parameter, no verification against authenticated user or deal membership.

**Current enforcement:** None. Placeholder role.

**Options:**
1. Implement DealService with membership lookup before mandate/agreement operations.
2. Require callers to provide role and trust it — violates security invariants.

**Recommendation:** Deal membership verification is missing. Phase A4 cannot be completed without it.

## 5. JSON/JSONB Mapping

**Evidence:**
- Schema: `mandates.must_have_deliverables JSONB NOT NULL`, `forbidden_terms JSONB NOT NULL`.
- `Mandate.java` maps these columns as `String mustHaveDeliverablesJson`, `String forbiddenTermsJson` with manual string builder in constructor.
- DTO `MandateDTO` defines `List<String> mustHaveDeliverables`, `List<String> forbiddenTerms`.
- No Jackson `@Column(columnDefinition="jsonb")` with `JsonType` usage found.

**Risks:** Manual string building is error-prone, no validation, no round-trip guarantee.

**Options:** Use Jackson `@JdbcTypeCode(SqlTypes.JSON)` with `List<String>` mapping, or custom converter.

**Recommendation:** Current mapping insufficient.

## 6. Untracked Implementation Files Inventory

**mandates/**
- `UtilityCalculator.java` — implements formulas from plan extract, uses BigDecimal.
- `Mandate.java` — JPA entity with String JSON columns.
- `MandateRepository.java` — standard Spring Data.
- `MandateService.java` — validation present, no auth/idempotency.
- `MandateController.java` — placeholder role, Idempotency-Key header read but not enforced.
- Test: `UtilityCalculatorTest.java` — 13 tests.

**agreements/**
- `Agreement.java` — JPA entity.
- `AgreementRepository.java` — standard.
- `AgreementService.java` — proposes agreement, deserializes JSON to DTO.
- `Rfc8785Canonicalizer.java` — Jackson sorted keys only.
- `Sha256Hasher.java` — standard Java MessageDigest.

**Known gaps:** Auth, idempotency, deal membership, RFC 8785 compliance, JSONB mapping, acceptance clearing on new version partially implemented.

## 7. Phase Dependency Graph Review

Current plan A1→A2→A3→A4→A5→A6→A7.

**Issues:**
- A1 requires clean working tree; untracked planning docs and skeleton files exist. Revision made to allow planning docs.
- A4 depends on deal membership service which does not exist.
- A5 blocked by RFC 8785 library decision.
- A3 JSONB handling depends on serialization decision.
- No artificial dependency between UtilityCalculator and Mandate model; could be parallel.

**Recommended dependency graph:**
- A1 → A2
- A2 parallel with A3 (model)
- A4 depends on A3 + auth + idempotency decision
- A5 depends on library approval only
- A6 depends on A5
- A7 depends on A2, A4, A6

## 8. Plan Consistency Findings

- Sub-plan traceability table lists requirements but file paths for contracts are correct.
- Phase A1 acceptance criteria updated to distinguish production changes from planning docs.
- Issues log missing fields for ISSUE-002 — corrected in second pass.
- No duplicate sections found.
- Incorrect assumption: Phase A1 expects DealService; does not exist.

## 9. Unresolved Questions Requiring Decision

1. Which Java library for RFC 8785 canonicalization, and verification method?
2. Retain or discard untracked WP-A2 skeleton files before approved phase start?
3. Idempotency implementation approach and timeline.
4. Deal membership verification strategy without DealService.
5. JSONB mapping strategy for mandate lists.

## 10. Files Created/Modified

Created: `docs/WP-A2-DECISION-PREPARATION.md`
No existing files modified.

Commands run: git status, git log, read files, grep searches, pom.xml inspection.
No tests executed; no code changes made.
