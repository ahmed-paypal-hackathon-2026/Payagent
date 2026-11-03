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

## Local commits on `work/all-open-fixes`

- `a265cd6` fix: evidence upload idempotency and principal compatibility
- `02d2757` test: cover auth 401 semantics and invitation status codes
