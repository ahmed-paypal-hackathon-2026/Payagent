# Payagent — Team Handoff Guide

**Source of truth:** `plan v2.docx` (extracted to `docs/_plan_extract.txt`), `AGENTS.md`, `README.md`.  
**Audience:** All six team members (Members A–F).  
**Status:** Current as of bootstrap completion (Phase 1–2). Implementation not started.

---

## 1. Ownership Boundaries (Quick Reference)

| Member | Role | Owned Paths | First Deliverable |
|--------|------|-------------|-------------------|
| **A** | Backend Domain, Contracts, Mandates, State Machine | `services/api/src/main/java/com/payagent/common/contracts/**`, `auth/**`, `deals/**`, `mandates/**`, `agreements/**`, `db/migration/**` | Canonical Java DTOs + Flyway V1 + JWT auth (`WP-A1`) |
| **B** | PayPal Sandbox & Settlement | `services/api/src/main/java/com/payagent/paypal/**`, `settlement/**` | PayPal OAuth + Invoice create/send (`WP-B1`) |
| **C** | Spring AI & Negotiation Agent | `services/api/src/main/java/com/payagent/ai/**`, `docs/AI_BENCHMARK.md` | Intake extractor + 20-prompt benchmark (`WP-C1`) |
| **D** | Web Workspace (Next.js) | `apps/web/**` | Deal Room UI on fixtures (`WP-D1`) |
| **E** | Infra, Security, Realtime, Evidence | `LICENSE`, `.gitignore`, `.env.example`, `docker-compose.yml`, `render.yaml`, `.github/workflows/ci.yml`, `evidence/**`, `disputes/**`, `realtime/**` | Docker Compose + CI + License (`WP-E1`) |
| **F** | Fixtures, QA, Release Gates, Docs | `shared/fixtures/**`, `services/api/src/test/java/com/payagent/releasegates/**`, `README.md`, `docs/SECURITY_THREAT_MODEL.md` | 10 canonical JSON fixtures (`WP-F1`) |

---

## 2. Shared Contract Change Protocol

**CRITICAL:** The canonical contracts live in **Java** (`Member A`) and are **mirrored in TypeScript** (`Member D`). They must stay in perfect sync.

### 2.1 Change Process
1. **Member A** modifies Java DTOs/Enums in `services/api/src/main/java/com/payagent/common/contracts/`
2. **Member D** updates `apps/web/src/types/contracts.ts` to match exactly
3. **Both** run parity test: `cd services/api && ./mvnw -B test -Dtest=ContractTypeScriptParityTest`
4. **Both** run fixture validation: `node scripts/validate-fixtures.mjs`
5. **Single coordinated PR** with both changes — no silent cross-boundary edits

### 2.2 What Triggers a Contract Change
- New enum value added
- DTO field added/removed/renamed
- Validation constraint changed (e.g., `@Min(0)` → `@Min(1)`)
- New DTO/Enum added to canonical set

### 2.3 What Does NOT Trigger a Contract Change
- Internal implementation changes (service logic, controller logic)
- New endpoints that use existing DTOs
- Test-only modifications

---

## 3. First Tasks by Member (Day 1–3)

### Member A — Domain Core
1. ✅ **Already done:** Canonical Java DTOs/Enums committed
2. ✅ **Already done:** Flyway `V1__init.sql` committed
3. **Do now:** Implement `JwtTokenProvider`, `JwtAuthFilter`, `AuthController` (`/api/v1/auth/login`, `/register`)
4. **Do now:** Implement `UtilityCalculator` with exact BigDecimal formulas from plan §2
5. **Do now:** Write unit tests for `UtilityCalculator` (buyer/seller utility, veto checks)
6. **Coordinate:** Confirm `contracts.ts` parity with Member D

### Member B — PayPal Adapter
1. **Do now:** Verify PayPal Developer Sandbox accounts (Business + Personal)
2. **Do now:** Run smoke test curl sequence:
   ```bash
   # OAuth
   curl -X POST https://api-m.sandbox.paypal.com/v1/oauth2/token \
     -u "$CLIENT_ID:$CLIENT_SECRET" -H "Content-Type: application/x-www-form-urlencoded" \
     -d "grant_type=client_credentials"
   # Create invoice
   curl -X POST https://api-m.sandbox.paypal.com/v2/invoicing/invoices \
     -H "Authorization: Bearer $ACCESS_TOKEN" -H "Content-Type: application/json" \
     -d '{"detail":{"note":"TEST_HASH","currency_code":"USD"},"items":[...]}'
   ```
3. **Do now:** Implement `PayPalAuthClient` with TTL token cache
4. **Coordinate:** Share Sandbox webhook URL with Member E (for ngrok tunnel)

### Member C — AI Engine
1. **Do now:** Add Spring AI starter to `pom.xml` (verify version compatibility with Spring Boot 3.3.5)
2. **Do now:** Configure `ChatClient` bean with `SPRING_AI_OPENAI_BASE_URL` support
3. **Do now:** Implement `IntakeExtractionService.extractDealIntake(String) → StructuredTermsDTO`
4. **Do now:** Create 20-sample benchmark prompts; run against configured LLM; document in `docs/AI_BENCHMARK.md`
5. **Coordinate:** Share prompt templates with Member F for adversarial test corpus

### Member D — Web Workspace
1. ✅ **Already done:** Next.js 14 scaffold + `contracts.ts` mirror + landing page
2. **Do now:** Build typed API client (`lib/api.ts`) with mock mode support
3. **Do now:** Build Mandate Builder form (buyer + seller) using `contracts.ts` types
4. **Do now:** Build Deal Room skeleton with fixture data (`shared/fixtures/`)
5. **Do now:** Verify `npm run typecheck && npm run lint && npm run build` pass
6. **Coordinate:** Confirm `contracts.ts` parity with Member A

### Member E — Infrastructure
1. ✅ **Already done:** `LICENSE`, `.gitignore`, `.env.example`, `docker-compose.yml`, `render.yaml`, `.github/workflows/ci.yml`
2. **Do now:** Verify Docker Compose starts cleanly locally (Postgres + API + Web)
3. **Do now:** Set up ngrok/cloud tunnel for PayPal webhook; give URL to Member B
4. **Do now:** Deploy staging to Render (or equivalent) for HTTPS/WSS
5. **Do now:** Implement `EvidenceStorageService` skeleton (interface + SHA-256 hashing)
7. **Do now:** Implement `DisputeService` skeleton (state transition to `DISPUTED`)

### Member F — Fixtures & QA
1. ✅ **Already done:** 10 canonical JSON fixtures committed to `shared/fixtures/`
2. ✅ **Already done:** Fixture validation script + README
3. **Do now:** Set up GitHub Issue board tracking WP-A1..WP-F2 + RG-1..RG-6
4. **Do now:** Draft `docs/SECURITY_THREAT_MODEL.md` (from plan §4, §16)
5. **Do now:** Coordinate with Member C on 15-case prompt-injection test corpus
6. **Do now:** Begin implementing TST-01 (mandate boundary) as integration test

---

## 4. PR Expectations

| Requirement | Details |
|-------------|---------|
| **Branch naming** | `feat/member-{A-F}/short-description` or `fix/member-{A-F}/...` |
| **PR title** | `[Member X] Brief description — relates to WP-XX` |
| **Description** | Link to work package, list files changed, note any contract changes |
| **Reviews** | Minimum 1 approval from another member (cross-boundary for shared contracts) |
| **CI checks** | All 6 CI jobs must pass: backend, frontend, fixtures, docker-compose-config, secret-scan, ai-slop-comments |
| **Contract changes** | Must include both Java + TypeScript updates + parity test pass |
| **No WIP merges** | All checks green before merge; no `--no-verify` |

---

## 5. Integration Order (Critical Path)

```
Week 1 (Oct 22–28):
  Day 1–2:  A1 (contracts/schema/auth), E1 (Docker/CI), F1 (fixtures), B1 (PayPal smoke)
  Day 3–4:  A2 (mandates, utility, agreement hash) ← C1 (intake extractor) ← D1 (UI on fixtures)
  Day 5–7:  Integration: A2 + C1 + D1 → two users negotiate + lock hash via REST

Week 2 (Oct 29–Nov 4):
  Day 8–10: B2 (Orders authorize/capture + webhooks), C2 (counteroffer agent), E2 (evidence + WS), D2 (settlement UI)
  Day 11–12: Full backend loop: Agreement → Invoice → Auth → Evidence → Capture

Week 3 (Nov 5–11):
  Day 13–15: Feature freeze (Nov 5), staging integration, bug fixes
  Day 16–18: Security verification (TST-01..TST-15), release gates
  Day 19–21: Demo recording, README finalization, Devpost submission (Nov 11 freeze)
```

**Key integration points:**
- Member A's `UtilityCalculator` + `AgreementService` must be ready before Member C's counteroffer agent (validates proposals)
- Member B's PayPal adapter must be ready before Member D's settlement panel (real IDs)
- Member E's WebSocket + Evidence must be ready before Member D's audit timeline + evidence panel

---

## 6. Communication & Coordination

| Channel | Purpose |
|---------|---------|
| **GitHub Issues** | Work package tracking (WP-A1..WP-F2), release gates (RG-1..RG-6) |
| **PR Comments** | Code review, contract change coordination |
| **Daily Sync (15 min)** | Standup: blockers, integration points, next 24h |
| **Shared `.env` template** | `.env.example` — each member fills their own secrets locally |

**Escalation:** If blocked >4 hours on cross-team dependency, escalate in daily sync.

---

## 7. Definition of Done (Per Work Package)

| WP | Done When |
|----|-----------|
| **WP-A1** | `./mvnw test -Dtest=AuthAndSchemaTest` passes; Buyer/Seller authenticate independently |
| **WP-A2** | UtilityCalculator formulas match plan §2 exactly; modifying 1 cent after buyer accept clears acceptance + new hash (TST-02) |
| **WP-B1** | Creates & sends real Sandbox Invoice (`INV2-...`) matching agreement to the cent |
| **WP-B2** | Full settlement loop works against live Sandbox: Invoice → Order Auth → Capture/Void + webhook verify |
| **WP-C1** | 100% JSON schema validity on 10 freelance prompts; p95 < 5s |
| **WP-C2** | 15 adversarial prompt-injection tests pass (0 mandate leaks/limit overrides) |
| **WP-D1** | Deal Room works end-to-end on fixtures (`NEXT_PUBLIC_USE_MOCKS=true`) by Oct 27 |
| **WP-D2** | Settlement panel shows real Sandbox IDs; evidence upload + accept/dispute works |
| **WP-E1** | Fresh clone → `docker compose up --build` starts in <3 min; CI passes; staging deployed |
| **WP-E2** | Cross-account IDOR → 403; dispute creation halts capture (TST-10, TST-11) |
| **WP-F1** | All 15 release gate tests + RG-1..RG-5 pass by Nov 8 |
| **WP-F2** | Devpost submitted by Nov 11 22:00 Cairo; public repo, license, demo URL, YouTube video |

---

## 8. Environment Setup for Each Member

| Member | Must Configure in Local `.env` |
|--------|--------------------------------|
| **A** | `PAYAGENT_JWT_SECRET` (generate 32+ chars) |
| **B** | `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`, `PAYPAL_WEBHOOK_ID` (from PayPal Developer Dashboard) |
| **C** | `SPRING_AI_OPENAI_API_KEY`, `SPRING_AI_OPENAI_BASE_URL` (LLM endpoint) |
| **D** | None (uses `NEXT_PUBLIC_USE_MOCKS=true` by default) |
| **E** | `PAYAGENT_EVIDENCE_SIGNING_SECRET` (generate 32+ chars) |
| **F** | None (reads fixtures, runs tests) |

**All members:** Copy `.env.example` → `.env`, fill required values, never commit.

---

## 9. Verification Commands Each Member Should Run

| Member | Commands |
|--------|----------|
| **A** | `cd services/api && ./mvnw -B test -Dtest=AuthAndSchemaTest,UtilityCalculatorTest,AgreementServiceTest` |
| **B** | `cd services/api && ./mvnw -B test -Dtest=PayPal*Test` + manual curl smoke test |
| **C** | `cd services/api && ./mvnw -B test -Dtest=IntakeExtractionTest,NegotiationAgentTest` + `docs/AI_BENCHMARK.md` updated |
| **D** | `cd apps/web && npm ci && npm run typecheck && npm run lint && npm run build` + visual UI check |
| **E** | `docker compose up --build -d && curl -f http://localhost:8080/actuator/health && curl -f http://localhost:3000` |
| **F** | `node scripts/validate-fixtures.mjs` + `cd services/api && ./mvnw -B test -Dtest=ReleaseGateSuite` |

---

## 10. Handoff Checklist (Owner → Team)

- [ ] Repository cloned by all 6 members
- [ ] Each member has local `.env` with their required secrets
- [ ] `docker compose up --build -d` works for all (or local backend/frontend alternative)
- [ ] `AGENTS.md`, `CLAUDE.md`, Cursor rules, OpenCode skills loaded in each member's IDE
- [ ] GitHub Issue board created with WP-A1..WP-F2 and RG-1..RG-6
- [ ] Member B has PayPal Sandbox accounts + webhook URL (from Member E's ngrok)
- [ ] Member C has LLM endpoint configured + Spring AI dependency added
- [ ] Member D can run `npm run dev` and see Deal Room on fixtures
- [ ] Member F has fixture validation passing + security threat model drafted
- [ ] All members understand contract change protocol (Section 2)
- [ ] All members know integration order and critical path (Section 5)
- [ ] Hard deadlines communicated: **Nov 11 22:00 Cairo freeze**, **Nov 12 22:00 Cairo official**

---

## 11. Current Blockers (As of Bootstrap)

| Blocker | Affected Members | Resolution |
|---------|------------------|------------|
| Maven wrapper download fails (network) | A, B, C, E, F (backend tests) | Use local Maven install / configure proxy / run in CI |
| Docker not installed | All (full stack) | Install Docker Desktop |
| Gitleaks not installed | E, F (secret scan) | Install Gitleaks / rely on CI |
| No PayPal sandbox creds | B | Member B creates accounts in PayPal Developer Dashboard |
| No LLM endpoint | C | Member C configures OpenAI-compatible endpoint |

---

## 12. Quick Links

- **Plan (extracted):** `docs/_plan_extract.txt`
- **Agent rules:** `AGENTS.md`
- **Architecture:** `docs/ARCHITECTURE.md`
- **Security model:** `docs/SECURITY_THREAT_MODEL.md`
- **Local dev:** `docs/LOCAL_DEVELOPMENT.md`
- **Agent setup:** `docs/AI_AGENT_SETUP.md`
- **Bootstrap status:** `docs/BOOTSTRAP_STATUS.md`
- **Fixtures:** `shared/fixtures/`
- **Contracts (Java):** `services/api/src/main/java/com/payagent/common/contracts/`
- **Contracts (TypeScript):** `apps/web/src/types/contracts.ts`
- **CI workflow:** `.github/workflows/ci.yml`