# Payagent integration QA — October 31, 2026

This review uses an isolated local combination of F (`d4c3867`), frontend PR #32 (`3c106bd`) and E (`d04e55a`). None of those integrations were merged into the main or original F checkout.

## Final verification results

- **Combined integration after new probes:** 296 cases — 278 passed, 5 failed, 13 skipped, 0 errors.
- **F branch after new probes:** 272 cases — 250 passed, 4 failed, 18 skipped, 0 errors. The five E-module probes explicitly skip when E is absent; their four positive results and duplicate-replay failure are only claimed for the combined checkout.
- New tracked defects: [duplicate evidence replay #33](https://github.com/mona585/Payagent/issues/33) and [cross-origin login preflight #34](https://github.com/mona585/Payagent/issues/34).
- The five browser scenarios below were observed with the in-app browser. The authored Playwright test file is syntax-checked but was not executed by the CLI runner.

## CI failure before this update

PR #30 backend CI fails because three enabled release probes reproduce actual application behavior: valid JWT deal creation returns 422, an invalid invitation replaces the seller, and expired JWT requests return 403 instead of the required 401. Other five jobs pass. Keep the expectations enabled; A owns fixes to authentication/deal behavior. F verifies them afterward.

## New verification

- The combined backend compiled and ran 290 tests: 274 passed, 3 failed, 13 skipped. These are the same three known failures; E's existing tests did not introduce another failure.
- The frontend2 production build passed, including its lint/type validation. PR #32 differs from PR #31 by an identity-keyed panel remount in MockDealWorkspace.
- Five new F evidence probes executed against real E services, real Spring security/JWTs, H2 and disposable filesystem storage: 4 passed, 1 failed.
- Passed: seller upload/buyer download reproduces the exact SHA-256 and bytes; outsider cannot list/download despite possessing a valid signed URL; buyer cannot upload seller evidence; a correctly signed expired URL is denied while the current issued URL succeeds. Expiry uses a controlled test signing key and past timestamp, not a 15-minute waiting test or a production key.
- Failed: two uploads with the same Idempotency-Key produce distinct evidence IDs/versions instead of replaying the original response. E's endpoint needs idempotency integration. The test asserts the documented mutation invariant; this is not evidence of a payment duplication.

## Browser checks performed on the running mock UI

Computer-use checks confirmed the explicit DEMO MODE disclosure, four scenario cards, reset of an unsaved 777.77 agreement edit after switching buyer to seller, completion of seeded synthetic dual consent, a 980.00→980.01 edit producing agreement v2/new hash/fresh consent, and opening a mock dispute removing capture controls while leaving the authorization visible.

Screenshots: `agreement-revision.jpg`, `mock-dispute-freeze.jpg`.

These were observed browser checks, not a Playwright CLI run. `browser-smoke.spec.cjs` captures five repeatable scenarios for a future automated runner. Its syntax was checked. Headless Chrome could not launch under the current process restrictions; the observed checks used the in-app browser instead. No live backend/PayPal/model calls were made by the mock UI.

## Additional confirmed browser/API blocker

With the default frontend/API origins (localhost:3000 → localhost:8080), real Spring MVC/security rejects the JSON login preflight with HTTP 403. `BrowserCorsReleaseGateIntegrationTest` confirms the failure on both the F branch and combined checkout. Configure intended origins or a same-origin proxy; this alone does not fix the JWT/UUID defect. See #34.

## Remaining integration gaps

- A's three defects still need fixes. Do not mute tests to make CI green.
- E evidence upload requires idempotency handling; dispute creation remains absent from E's inspected branch.
- The frontend live capabilities explicitly mark workspace snapshots, offer history, current-agreement reads, negotiate-step, evidence, disputes and audit/realtime integration as pending. Some E endpoints now exist but the frontend wiring must be reviewed together.
- E's branch is behind current main and has no integration PR. #31/#32 overlap; the team should choose the intended frontend PR rather than merge both blindly.
- Real Sandbox approval/authorize/capture/void, verified webhook proof, PostgreSQL/concurrency, deployment and actual video recording remain unverified.

A full product demo remains blocked. A labelled mock frontend walkthrough can now be rehearsed with the actual new UI.

## Re-running the browser test file

Use an isolated test-runner directory with `@playwright/test` installed and the frontend2 build running in mock mode. Run `PAYAGENT_BROWSER_BASE_URL=http://localhost:3100 npx playwright test --config=playwright.config.cjs`. Do not run these mock assertions as a substitute for live Sandbox integration tests. Each Playwright case uses its own browser context.
