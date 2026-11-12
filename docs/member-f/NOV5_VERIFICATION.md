# Member F verification — November 5, 2026

Integration source: PR #41 (`c4f818df`), now included in main by `0b17ac37`. Member F tests, fixtures and documentation are layered on that application code. No deployment patch or teammate implementation edit is included in this F update.

## Results

| Check | Result |
|---|---|
| Clean full backend (`mvn -B clean test`, Java 21) | PASS: 405 total, 392 passed, 0 failures/errors, 13 skipped |
| Member F subset within that run | PASS: 69 total, 60 passed, 0 failures/errors, 9 skipped |
| Backend package (`-DskipTests package`) | PASS |
| Ten canonical fixtures | PASS |
| Contract parity | PASS in the full backend suite |
| Frontend clean install, lint, typecheck, production build | PASS; build used `NEXT_PUBLIC_USE_MOCKS=false` and localhost API |
| Source-comment and whitespace checks | PASS |
| Secret scanning | REVIEWED FINDING; not a clean scanner exit |
| Docker Compose/PostgreSQL runtime | BLOCKED: Docker unavailable locally |
| Real Sandbox/deployed-app probes | NOT RUN: credentials, genuine records and deployed environment required |
| Final product video/YouTube/Devpost | NOT DONE; storyboard rehearsal only |

The 13 skips comprise nine opt-in Member F probes (three Sandbox, six deployed-app) and four opt-in live-model cases. They are not passes.

## Changes in this F follow-up

- Adapt JWT filter and controller calls to current constructor/method signatures.
- Stub the locking agreement/invitation repository methods now used by implementation code; supply a valid pending-acceptance deal for the agreement unit probe.
- Assert early expired-token rejection is 401, consistent with the security contract.
- Add three real JWT/H2 integration controls: create a deal, reject a wrong invite while preserving the seller, and allow the authorized buyer to read an existing settlement. The last case seeds a clearly synthetic payment row; it makes no PayPal call.
- Preserve the security expectations: no disabled regressions, blanket skips or relaxed assertions.

Evidence upload replay, authorized download/hash, outsider denial, role denial and expired-link rejection pass on the integrated E module. Issues #26/#27/#28/#34 were previously closed; #33 was closed after PR #41's fix passed the evidence replay probe.

## Remaining limits

The current frontend lockfile audit reports 11 findings: 1 critical, 4 high, 6 moderate. Dependency changes remain with D/E. The unmodified Render/application deployment configuration needs review of JDBC database wiring, the unsupported `username` database property, configurable CORS, evidence signing secret and persistent storage. Render already forwards environment variables as Docker build arguments, so that alone does not require a Dockerfile change. No proposed deployment patch was applied.

Gitleaks history and tracked-source scans match a static test `Idempotency-Key` (`offer-invalid-1`) as generic-api-key. This is a false positive reviewed against its test context. Scanning generated reports also matches disposable test JWTs and echoed headers. Raw test reports and generated build output are not committed. No scanner rule was weakened and no blanket allowlist was added.

The extra `validate-web-fixtures.mjs` script returns five source-text failures because it expects rendered elements and links directly in route files while the UI uses imported components. This script is not part of the passing CI gates; its result is not represented as browser verification.

Local H2 tests do not establish PostgreSQL migrations, cross-process/concurrent payment safety, live browser operation or a complete PayPal journey. The live capability list still marks the bounded-agent `negotiate-step` endpoint as pending. Live payment/model behavior remains unverified.

## Release decision

HOLD for public release/submission. Required next: owning-member deployment/dependency work, real Sandbox records and delivered-webhook proof, actual buyer/seller/Sandbox recording, verified organizer requirements, public YouTube link and Devpost submission. The existing rehearsal must not be described as actual product footage.

References: [Render Docker environment/build behavior](https://render.com/docs/docker), [Render blueprint properties](https://render.com/docs/blueprint-spec), [Next.js security update](https://nextjs.org/blog/security-update-2025-12-11).
