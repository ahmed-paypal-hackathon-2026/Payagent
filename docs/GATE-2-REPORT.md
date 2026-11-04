# Gate 2 Report — Execution & Evidence

**Gate:** 2 (Oct 28, 21:00 Cairo per plan §5)
**Acceptance criteria (plan §5):** (1) Two users negotiate and lock an identical `termsHashSha256` via REST API; (2) Spring AI extracts structured terms in under 5s; (3) UI Deal Room works end-to-end on fixtures.
**Date:** 2026-10-25
**Author:** AI backend agent (self-review only; no independent review performed)

---

## 1. Criterion Status

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | Two users negotiate and lock identical `termsHashSha256` via REST API | **PASS** | `Gate2Criterion1IntegrationTest` — 3 tests, 0 failures |
| 2 | Spring AI extracts structured terms in under 5 seconds | **PARTIAL** | Local fixture path: 14 tests, 0 failures, latency < 0.05s. Live-provider p95: **NOT MEASURED** — requires Spring AI dependency + LLM credentials |
| 3 | UI Deal Room works end-to-end on fixtures | **PASS** | `npm run build` succeeds with `/deals/[dealId]` route; `node scripts/validate-web-fixtures.mjs` — 31/31 checks pass |

---

## 2. Criterion 1 — PASS

**Test:** `services/api/src/test/java/com/payagent/Gate2Criterion1IntegrationTest.java`

Exercises the real application flow with two distinct authenticated participants through the REST API:

1. Deal created with buyer and seller
2. Buyer creates mandate via `POST /api/v1/deals/{id}/mandates` → 201
3. Seller creates mandate via `POST /api/v1/deals/{id}/mandates` → 201
4. Buyer submits offer via `POST /api/v1/deals/{id}/offers` → 201, `mandateCompliant=true`, `callerUtilityScore="1.0000"`
5. Buyer proposes agreement via `POST /api/v1/deals/{id}/agreements` → 201, version 1, 64-hex `termsHashSha256`
6. Buyer accepts via `POST /api/v1/deals/{id}/acceptance` → 200; deal stays `TERMS_PENDING_ACCEPTANCE`
7. Seller accepts via `POST /api/v1/deals/{id}/acceptance` → 200; deal transitions to `AGREED`
8. Both parties resolve to the **identical** `termsHashSha256` (asserted)

Negative tests:
- `unauthorizedUserCannotParticipate` — outsider gets 403 on mandate access and offer creation
- `invalidTermsCannotBeAccepted` — non-compliant offer (price over mandate limit) gets 422

```
cd services/api && ./mvnw.cmd -B test -Dtest=Gate2Criterion1IntegrationTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 3. Criterion 2 — PARTIAL

**Implemented:** `com.payagent.ai.IntakeExtractionService` (production) + `FixtureChatClient` (test)

- Strict JSON-schema system instruction
- Markdown-fence stripping (models that wrap JSON in ` ```json `)
- Per-field validation: rejects missing `title`, empty `deliverables`, negative price, zero delivery days, null fields
- `StructuredTermsDTO` output validated against the canonical contract

**Tests:** `IntakeExtractionServiceTest` — 14 tests, 0 failures

```
cd services/api && ./mvnw.cmd -B test -Dtest=IntakeExtractionServiceTest
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**Latency (local fixture path):**
- Single extraction: < 0.05s (asserted < 5.0s)
- 10-prompt sweep worst case: < 0.05s (asserted < 5.0s)
- Schema validity: 100% (all fields validated)

**NOT claimed:** live-provider p95 latency. The local fixture path removes network latency entirely.

**Remaining blocker:** production `ChatClient` requires:
1. `spring-ai-openai-spring-boot-starter` dependency in `pom.xml`
2. `SPRING_AI_OPENAI_API_KEY` and `SPRING_AI_OPENAI_BASE_URL` credentials
3. 20-prompt live benchmark recorded in `docs/AI_BENCHMARK.md`

Full details in `docs/AI_BENCHMARK.md`.

---

## 4. Criterion 3 — PASS

**Implemented:** Deal Room at `apps/web/src/app/deals/[dealId]/page.tsx`

- Loads fixture workspace via `apps/web/src/lib/api.ts` (`NEXT_PUBLIC_USE_MOCKS=true`)
- Uses canonical contracts from `apps/web/src/types/contracts.ts` (no parallel schema)
- Displays: deal status, buyer/seller, red/green term diff table, offer history with utility scores, dual agreement signature card with `termsHashSha256`
- Accept button per role (Buyer/Seller) with loading and error states
- Root page links to the Deal Room

**Commands and results:**

```
cd apps/web
npm run typecheck   → PASS (tsc --noEmit, no errors)
npm run lint        → PASS (✔ No ESLint warnings or errors)
npm run build       → PASS (✓ Compiled successfully, /deals/[dealId] route generated)

node scripts/validate-web-fixtures.mjs → 31/31 checks pass
```

**Fixture validation checks (31):**
- `01-buyer-intake.json` matches `StructuredTermsDTO` contract (7 fields)
- `06-accepted-agreement-snapshot.json` matches `AgreementSnapshotDTO` contract (7 fields)
- `termsHashSha256` is 64 hex chars
- Both `buyerAcceptedAt` and `sellerAcceptedAt` present
- Agreement price consistent with intake (98000 ≥ 90000)
- Deal Room renders `termsHashSha256`, dual acceptance, offer history, accept button
- API client uses canonical contracts, supports `NEXT_PUBLIC_USE_MOCKS`, implements `fetchWorkspace` and `acceptAgreement`

---

## 5. Full Verification Results

| Check | Command | Result |
|---|---|---|
| Backend suite | `./mvnw.cmd -B test` | **137 tests, 0 failures, 0 errors, 1 skipped, BUILD SUCCESS** |
| Gate 2 Criterion 1 | `./mvnw.cmd -B test -Dtest=Gate2Criterion1IntegrationTest` | **3 tests, 0 failures, BUILD SUCCESS** |
| AI extraction | `./mvnw.cmd -B test -Dtest=IntakeExtractionServiceTest` | **14 tests, 0 failures, BUILD SUCCESS** |
| Frontend typecheck | `npm run typecheck` | **PASS** (no type errors) |
| Frontend lint | `npm run lint` | **PASS** (no warnings or errors) |
| Frontend build | `npm run build` | **PASS** (compiled, 5/5 pages generated) |
| Shared fixtures | `node scripts/validate-fixtures.mjs` | **10/10 OK** |
| Web fixtures | `node scripts/validate-web-fixtures.mjs` | **31/31 OK** |
| `git diff --check` | (tracked files only) | Clean (one LF/CRLF warning on tracked `page.tsx`, no whitespace errors) |

---

## 6. Files Changed

### Production (new)
| File | Purpose |
|---|---|
| `services/api/src/main/java/com/payagent/ai/ChatClient.java` | LLM client abstraction |
| `services/api/src/main/java/com/payagent/ai/IntakeExtractionService.java` | Structured intake extractor with strict schema validation |
| `apps/web/src/lib/api.ts` | Fixture-backed API client using canonical contracts |
| `apps/web/src/app/deals/[dealId]/page.tsx` | Deal Room UI (term diff, offer history, dual acceptance, termsHashSha256) |
| `docs/AI_BENCHMARK.md` | AI benchmark record with local measurements and pending live verification |

### Production (modified)
| File | Change |
|---|---|
| `apps/web/src/app/page.tsx` | Added link to Deal Room |

### Tests (new)
| File | Tests |
|---|---|
| `services/api/src/test/java/com/payagent/Gate2Criterion1IntegrationTest.java` | 3 |
| `services/api/src/test/java/com/payagent/ai/FixtureChatClient.java` | (test infra) |
| `services/api/src/test/java/com/payagent/ai/IntakeExtractionServiceTest.java` | 14 |
| `scripts/validate-web-fixtures.mjs` | 31 checks |

### Documentation (new/updated)
| File | Purpose |
|---|---|
| `docs/AI_BENCHMARK.md` | AI benchmark: local measurements, pending live verification |
| `docs/GATE-2-REPORT.md` | This report |

---

## 7. Remaining External Prerequisites

| Item | Blocks | Owner |
|---|---|---|
| Spring AI dependency + LLM credentials (`SPRING_AI_OPENAI_API_KEY`, `SPRING_AI_OPENAI_BASE_URL`) | Criterion 2 live-provider p95 measurement | Member C |
| PayPal Sandbox credentials (`PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`, `PAYPAL_WEBHOOK_ID`) | Gate 3 settlement | Member B |

---

## 8. Git State

```
git status --short → 1 modified tracked file (apps/web/src/app/page.tsx), all other work in untracked paths
git diff --check   → clean (one LF/CRLF line-ending warning on the tracked file; no whitespace errors)
git stash list     → empty
```

Nothing staged, committed, pushed, stashed, reset, cleaned, or branch-switched.

---

## 9. Next Highest-Priority Deliverable

1. **Criterion 2 completion (Member C):** add `spring-ai-openai-spring-boot-starter`, implement the Spring AI-backed `ChatClient`, run the 20-prompt live benchmark, record p95 in `docs/AI_BENCHMARK.md`.
2. **Gate 3 (Oct 29 – Nov 02):** PayPal Sandbox settlement (Member B) — OAuth2, Invoicing v2, Orders v2 AUTHORIZE, Payments v2 CAPTURE/VOID, webhook signature verification.
3. **WP-E2 (Member E):** Evidence storage, dispute freeze, WebSocket STOMP broker.
