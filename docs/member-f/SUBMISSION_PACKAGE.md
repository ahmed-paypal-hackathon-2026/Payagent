# Payagent — submission package

Prepared October 29, 2026. This copy is ready for editing and review. The organizer's event URL and actual form requirements have not been supplied, so eligibility, categories, deadline and field limits remain unverified. Do not submit this as a complete working-product claim.

## Project name

Payagent

## One-line description

Bounded AI negotiation connecting freelance agreements to a PayPal Sandbox settlement workflow.

## Inspiration

A client and a freelancer can agree on a price while still disagreeing about scope, revisions, deadlines, or what constitutes delivery. Payagent explores a structured way to connect those decisions to consent and payment records.

## What it does

The target experience gives each participant a private mandate, proposes terms within delegated boundaries, and requires both parties to accept the same versioned agreement. The intended settlement journey connects that agreement to a PayPal Sandbox invoice and a separate authorization/capture workflow, followed by delivery evidence and acceptance or dispute.

Current implementation includes backend domain/authentication/agreement code, a PayPal adapter, deterministic negotiation and optional model support, plus an initial frontend landing page. Evidence, dispute creation, realtime and the complete Buyer/Seller interface remain outstanding. The public description must be updated to the actual state of the final recorded build.

## How we built it

The repository uses a Java 21/Spring Boot backend, PostgreSQL schema and a Next.js frontend. Financial amounts use integer minor units. Negotiation policy and mandate checks are deterministic; optional model output can assist extraction and wording. Agreement versions use canonical terms and SHA-256 fingerprints.

The PayPal adapter targets Sandbox Invoicing, Orders, Payments and webhook verification. The PayPal AI Toolkit supplied development guidance during the Member F work. This does not imply runtime MCP integration, and Spring AI is planned rather than a current dependency.

## Member F contribution

- Reviewed the ten existing canonical fixtures; added semantic/DTO/amount/hash consistency checks and corrected a mismatched agreement hash.
- Replaced a disabled placeholder with executable JUnit and MockMvc release probes, including real Spring security/H2 integration checks.
- Added 15 adversarial input cases and 15 malicious output cases, plus consent, idempotency, webhook, membership and JWT checks.
- Added opt-in real Sandbox and deployed-app probes; they remain skipped until suitable disposable test data and configuration are supplied.
- Reproduced and filed implementation defects rather than accepting insecure behavior as a passing test.
- Created the work-package/release tracking checklist and prepared the recording script, captions and 1080p narrated storyboard rehearsal.

## Challenges

A significant integration issue was that authentication created an email-named principal while domain controllers expected a UUID. An invitation regression also showed an invalid invitation replacing the assigned seller. A real security-chain test identified 403 versus the required 401 for expired tokens. These are tracked as implementation-owner fixes; the release suite remains red until corrected.

## What we learned

A test that supplies a convenient principal or a mocked authorization decision can pass while the real request path still fails. Separating unit probes, real security/database integration, deployed-app tests and live payment proof makes the limits of each result visible.

## Accomplishments and results

Use the final verification report for exact counts. Do not describe skipped live tests as passes, the complete product as deployed, or a storyboard rehearsal as a working-product recording. The current release decision is NO-GO until documented defects and missing integrations are resolved.

## Future work

Complete the Buyer/Seller UI, private evidence lifecycle, dispute/realtime implementation, shared capture/dispute concurrency protocol, dependency upgrades and independently verified Sandbox workflow. Validate the complete public experience before submission.

## Links and assets

- Repository: https://github.com/mona585/Payagent
- Tracking checklist: https://github.com/mona585/Payagent/issues/29
- Proposed F branch: `member-f/release-gates`
- Local rehearsal: `STORYBOARD_REHEARSAL_1080p.mp4` — **not final product footage**.
- Local narration and scene actions: `DEMO_SCRIPT.md`.
- Public live-demo URL: **not available/verified**.
- Final public YouTube URL: **not uploaded**.

## Final checks before publishing

- Confirm actual organizer/event page, eligibility, deadline, prize rules and disclosure requirements.
- Record the completed app with buyer, seller and real Sandbox records. Confirm IDs/amounts on the actual test deal.
- Replace this package's implementation-status statements with the verified state of that build.
- Verify public repository, license, deployed app and YouTube link in a signed-out browser.
- Check the final video is 1080p and under three minutes. This kit's rehearsal is approximately 2:40.
- Include accurate model/provider, fallback, Sandbox and toolkit disclosures.
- Confirm the user/team approves the final concrete submission content and links before submitting the form.
