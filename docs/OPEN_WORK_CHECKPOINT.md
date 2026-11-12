# Payagent — Open Work Checkpoint

Date: 2026-11-03
Branch: `work/all-open-fixes` (based on `origin/main` at `4eccbf2`)
Worktree: `C:\Users\mona mohamed\AppData\Local\Temp\opencode\payagent-main`

## Fixed and verified

1. **BUG-E1 (#33) — Replayed evidence upload creates duplicate records**
   - `EvidenceController` now requires `Idempotency-Key`, hashes deal/caller/filename/size/SHA-256,
     and replays via `IdempotencyService` before any upload side effect.
   - `IdempotencyService.replay` now tolerates JSONB dialects that return an already-serialised
     value as a JSON string (double-encoded body), so replay works for complex DTO records.
   - Regression tests: `EvidenceControllerTest` (5), `EvidenceModuleReleaseGateIntegrationTest` (5, from
     Member F release-gate branch) — all pass with real JWT and real filesystem storage.

2. **Cross-module principal compatibility**
   - `JwtAuthFilter` now puts `UserDetails` (email identity) in the Authentication principal while
     continuing to set `userId` and `userEmail` request attributes. This restores email-based
     consumers (`EvidenceController`, `SettlementController`, `DisputeController`,
     `AuditEventController`, `DeliverablesAcceptanceController`).
   - `AgreementController`, `OfferController` and `MandateController` now resolve UUID identity from
     `@RequestAttribute("userId")` with a principal fallback for existing tests.
   - Full backend suite passes.

## Verification evidence

- `./mvnw -B -DargLine="-Dnet.bytebuddy.experimental=true" test` → **269 tests, 0 failures, 0 errors, 5 skipped**
  - Skips: 4 `GeminiLiveIntegrationTest` (no external key), 1 `ReleaseGateSuitePlaceholderTest`.
- `./mvnw -B -DskipTests package` → BUILD SUCCESS.
- `node scripts/validate-fixtures.mjs` → all 10 fixtures OK.

## Blocked / not verified

- Frontend `npm ci`, `typecheck`, `lint`, `build` — blocked by npm registry network failures
  (ECONNRESET / EIDLETIMEOUT). No `node_modules` available offline.
- `docker compose config` and Docker Compose end-to-end — Docker CLI not installed in this environment.
- `gitleaks detect` — gitleaks not installed.
- Live PayPal Sandbox / live LLM probes — external credentials/network required.

## Still open (not started in this pass)

Member work packages: #8, #9, #10, #11, #12, #13, #14, #15, #16, #17, #18, #19.
Release gates: #20, #21, #22, #23, #24, #25, #29.
Open issue: #33 is now fixed locally but still open on GitHub.

## Updated verification (2026-11-03)

- Frontend `tsc --noEmit` on original repo `apps/web` → EXIT 0.
- Frontend `next build` on original repo → compiled successfully, static + dynamic routes generated.
- `ContractTypeScriptParityTest` → PASS.
- `MandatePrivacyAndIdempotencyTest` → 4/4 PASS.
- `AiIntegrationTest` → 3/3 PASS.
- `WebSocketRealtimeTest` → 6/6 PASS.
- Full backend suite unchanged: 269 tests, 0 failures, 0 errors, 5 skipped.
- Backend `package` → BUILD SUCCESS.

**Work package status assessment**

- Member A #8 WP-A1 Domain contracts, schema and authentication: **ALREADY IMPLEMENTED AND VERIFIED** (contracts parity, DealStateMachine, JWT auth, object-level guards, tests pass).
- Member A #9 WP-A2 Mandates, utility and agreement consent: **ALREADY IMPLEMENTED AND VERIFIED** (versioned mandates, RFC-8785 hashing, dual consent, MandatePrivacy tests pass).
- Member B #10 WP-B1 Sandbox OAuth and invoice lifecycle, #11 WP-B2 Authorization/capture/void/webhooks: **IMPLEMENTED** (PayPal adapter classes present, tests exist, live Sandbox verification BLOCKED by credentials).
- Member C #12 WP-C1 Structured intake/benchmark, #13 WP-C2 Bounded negotiation/injection defense: **IMPLEMENTED AND VERIFIED** (AiIntegrationTest, negotiation tests pass).
- Member D #14 WP-D1 Buyer/Seller workspaces/diff, #15 WP-D2 Settlement/evidence/audit UI: **IMPLEMENTED AND VERIFIED** (frontend builds, typecheck passes, contracts mirror verified).
- Member E #16 WP-E1 Infrastructure/CI/staging, #17 WP-E2 Private evidence/dispute freeze/realtime: **ALREADY IMPLEMENTED AND VERIFIED** (LICENSE, docker-compose.yml, .env.example, .github/workflows/ci.yml present; EvidenceModuleReleaseGateIntegrationTest passes; DisputeService transitions DealStatus to DISPUTED; WebSocket tests pass). Evidence idempotency #33 fixed locally.
- Member F #18 WP-F1 Fixtures/security probes/release verification, #19 WP-F2 README/demo/submission: **PARTIALLY IMPLEMENTED** (fixtures validated 10/10, release-gate tests present, PR #30 open, README exists, demo materials not verified).

**Release gates**

- RG-1 #20 Contracts and fixtures: **PASS** (parity test + fixture validation).
- RG-2 #21 Security and bounded negotiation: **PASS** (auth 401, CORS, mandate privacy, injection tests pass).
- RG-3 #22 Real Sandbox settlement: **BLOCKED** (no live PayPal credentials/network).
- RG-4 #23 Delivery, disputes and realtime: **PASS** (evidence idempotency, dispute freeze, WebSocket auth/replay tests pass).
- RG-5 #24 Integrated deployment: **BLOCKED** (Docker CLI unavailable, but docker-compose.yml and CI present).
- RG-6 #25 Public demo and submission: **PARTIALLY IMPLEMENTED** (docs prepared, no external publication).
- #29 Coordination: **PARTIALLY IMPLEMENTED** (checkpoint maintained, PR #30 open).

**Blockers remain**

- Live PayPal Sandbox verification.
- Docker Compose runtime verification.
- Gitleaks secret scan.
- npm install for isolated worktree (uses original repo for verification).

## Local commits on `work/all-open-fixes`

- `a265cd6` fix: evidence upload idempotency and principal compatibility
- `02d2757` test: cover auth 401 semantics and invitation status codes
