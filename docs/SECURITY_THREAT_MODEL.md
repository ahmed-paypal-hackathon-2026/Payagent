# Payagent — Security Threat Model

**Source of truth:** `plan v2.docx` (extracted to `docs/_plan_extract.txt`), `AGENTS.md` §4, `.cursor/rules/security.mdc`.  
**Status:** Current verification is in the Member F ledger below. Later planning tables are retained as historical design intent.

---

## Current verification — November 5, 2026

The integrated PR #41 candidate plus the complete F suite passes **405 backend cases: 392 passed, 13 skipped, 0 failures/errors**. F accounts for 69 cases (60 passed, 9 skipped). Earlier dated results below remain historical evidence, not current failures.

- Issues #26/#27/#28/#34 were closed after verification. PR #41's identity compatibility and evidence idempotency changes now pass the real-JWT/H2/filesystem probes, including same-key evidence replay and a seeded authorized settlement positive control.
- The agreement mock now uses the locking repository lookup and a valid pending-acceptance deal. Security test setup follows the new constructor/method signatures; no assertions were weakened or gates disabled.
- The three PayPal and six deployed-app F cases remain skipped. Four live-model cases also skip. No actual invoice/order/capture/void journey was performed in this verification.
- Frontend dependency audit reports 11 findings (1 critical, 4 high, 6 moderate) on the current lockfile. Next.js is pinned to 14.2.18. Resolve applicable findings before public deployment.
- Tracked-source and history Gitleaks scans flag a literal test `Idempotency-Key` (`offer-invalid-1`) as generic-api-key. This is a reviewed false positive, not a credential. A whole-working-directory scan additionally matches generated test reports containing disposable JWTs and echoed headers; these reports are not committed. Raw scanner status is not described as clean.
- Docker/Compose is unavailable here. H2 does not certify PostgreSQL/Flyway deployment. The current Render configuration also needs JDBC URL, allowed-origin, build-argument, evidence signing-secret and persistent-storage corrections.

**Release decision: HOLD.** Real Sandbox verification, deployment and final public demo/submission remain required. See [the current report](member-f/NOV5_VERIFICATION.md).

---

## October 31 integration review

Latest evidence is in the [integration report](member-f/oct31-review/REVIEW.md). Earlier dated records below are retained as historical results.

- F branch with new probes: **272 cases, 250 passed, 4 failed, 18 skipped, 0 errors**.
- Isolated combination of F (`d4c3867`), frontend2 (`3c106bd`) and E (`d04e55a`), with new probes: **296 cases, 278 passed, 5 failed, 13 skipped, 0 errors**. No integrated code was merged into the original F branch or main.
- `EvidenceModuleReleaseGateIntegrationTest` runs when E's module is present and explicitly skips otherwise. Real JWT/H2/filesystem probes verified seller upload/buyer SHA-256-correct download, outsider denial even with a valid URL, buyer upload denial, and rejection of a mathematically valid but expired signature. This advances local TST-10/12/13 coverage; production expiry, PostgreSQL and deployed URLs remain unverified.
- The evidence replay test fails: identical Idempotency-Key uploads create distinct records/versions ([#33](https://github.com/mona585/Payagent/issues/33)).
- Real login preflight from the default frontend origin returns 403 ([#34](https://github.com/mona585/Payagent/issues/34)). Browser live-mode integration needs an allowed-origin policy or a same-origin proxy, in addition to the three existing A fixes.
- Frontend2 production build passed. Browser checks confirmed mock-mode disclosure, unsaved form reset on identity switch, synthetic dual consent, one-cent revision invalidation, and mock dispute freeze. Screenshots and a rerunnable, syntax-checked browser test file are in the report directory. The Playwright CLI file has not been run; actual browser observations used the in-app browser.

**Release remains NO-GO.** None of these mock browser actions constitutes real PayPal proof. Do not disable regression tests to obtain a green check.

---

## Member F verification ledger — 2026-10-29

**Release decision: NO-GO.** The historical planning tables below describe design intent, not current certification.

### Actual execution results

**Full backend:** 266 cases: 250 passed, 3 failed, 13 skipped, 0 errors. **F-owned release suite:** 60 cases: 48 passed, 3 failed, 9 skipped. Four other skips are opt-in Gemini live-model cases. The nine F skips are three real Sandbox and six deployed-app integration probes; they are not passing evidence.

The three reproduced defects are tracked in [BUG-A1](https://github.com/mona585/Payagent/issues/26), [BUG-A2](https://github.com/mona585/Payagent/issues/27), and [BUG-A3](https://github.com/mona585/Payagent/issues/28): valid JWT deal creation returns 422; an invalid invitation replaces the seller; expired JWTs return 403 instead of the required 401. The expiry case denies access and is a response-contract defect.

**Passed:** fixture syntax (10 files), semantic/hash consistency, TypeScript contract parity, source-comment checks, frontend lint/typecheck/production build for the current landing page, and whitespace checks. Gitleaks v8.30.1 scanned 26 Git commits and the F-owned snapshot with **zero findings**. This is scanner evidence, not a guarantee that no secret can exist.

**Dependency audit:** 5 findings (4 high, 1 critical), including Next.js; D/E assessment and upgrades remain outstanding. **Docker/Compose:** blocked locally by missing Compose support. Complete two-browser UI, private evidence/dispute/realtime, real Sandbox lifecycle, automatic UNKNOWN recovery and final public demo remain unverified. A read-only Sandbox connectivity request returned HTTP 403 with the available token; no invoice/order was created, sent, captured or voided.

The local work is on `member-f/release-gates`. Release decision remains **NO-GO** until failed and blocked gates are resolved. The GitHub [tracking checklist](https://github.com/mona585/Payagent/issues/29) contains all twelve work packages and six explicitly proposed gate definitions. No individual teammate approval is inferred.

### Requirement coverage

| Requirement | Implemented F probes | Remaining limit |
|---|---|---|
| TST-01 mandate boundary | Exact buyer ceiling/seller floor and one-cent violations; canonical over-budget fixture | Full negotiation HTTP journey remains unverified |
| TST-02 consent invalidation | Real service and H2 persistence: one-cent edit, new hash/version, old consent cleared, stale acceptance rejected | PostgreSQL/concurrency verification remains |
| TST-03 injection privacy | 15 malicious output cases plus 15 actual input/sanitizer/extractor cases | Arbitrary live model and WebSocket privacy are not certified |
| TST-04 unauthorized action | Tool-call-shaped text has no effect on extracted terms; policy/financial decision remains unchanged by model prose | No complete agent-to-payment tool authorization journey exists |
| TST-05 fallback | Unavailable/timeout exception retains deterministic offer | Actual transport timeout/p95 not measured by this probe |
| TST-06 invalid output | Missing/negative/currency-invalid terms rejected; tool-shaped JSON does not become a payment action | Live provider schema proof remains |
| TST-07 webhook forgery | Controller boundary and real Spring security chain reject unsigned/unverified payload before persistence | Real recorded-event signature verifier probe implemented, skipped until configured |
| TST-08 deduplication | Sequential replay tested with both controlled repositories and real H2 persistence | Concurrent replay/PostgreSQL not certified |
| TST-09 idempotency | Stored response replay and differing payload rejection | Concurrent/cross-user scope not certified |
| TST-10 IDOR | Real membership denial, mandate controller 403, real JWT outsider settlement 403; identity/invitation regressions exposed | Evidence/deal deployed probes implemented, skipped while E/D integration is missing |
| TST-11 dispute freeze | Existing dispute prevents any PayPal capture call | Deployed create-dispute/acceptance probe implemented; missing E/B integration and race proof |
| TST-12 evidence authorization/hash | Deployed outsider-denial and authorized-download digest probes implemented | Skipped until genuine issued URL and disposable test deal exist |
| TST-13 expiry | Deployed test requires a genuine issued URL after its 15-minute TTL, expects 403 | Skipped until E implements signed links; no fabricated signature or blocking wait |
| TST-14 expired JWT | Provider/filter deny expired token; real protected-route integration asserts 401 | Fails: application returns 403; owner A must fix contract |
| TST-15 UNKNOWN | Explicit reconciliation preserves UNKNOWN until PayPal returns known state | Automatic invocation/scheduling not verified |

### Implementation-owner handoff

- **A:** Resolve [JWT identity](https://github.com/mona585/Payagent/issues/26), [invitation validation](https://github.com/mona585/Payagent/issues/27), and [expired-token status](https://github.com/mona585/Payagent/issues/28). Keep the regression expectations intact and re-run the full suite.
- **B/E:** Define and prove a common dispute/capture concurrency protocol. Source inspection shows the dispute check occurs before a process-local capture lock. A unit check of an existing dispute does not certify simultaneous requests or multiple instances.
- **E:** Supply private evidence upload/download, signed expiry, dispute creation and realtime endpoints so staged integration probes can execute.
- **D:** Supply the complete two-session UI. The current landing page cannot establish the final browser journey or provide actual product-demo footage.
- **B:** Supply real Sandbox invoice/order IDs, expected amount/currency, and a real delivered webhook proof outside Git. Simulator events are not valid substitutes for signature proof. Confirm automatic UNKNOWN recovery.
- **D/E:** Review and resolve the frontend dependency audit and prove clean-clone deployment.
- **A/D:** Review fixture/DTO parity through WP-A1/WP-D1; F inspected the contracts locally but has not obtained teammate approval.

**Fresh local clone verification:** the committed branch compiles and reproduces the same release-suite result (60 cases: 48 passed, 3 failed, 9 skipped). Fixture and source-comment checks also pass there. This used a pre-populated Maven cache; it does not certify network bootstrap, Docker startup, PostgreSQL, or staging. A post-commit history scan covered 27 commits with zero findings.

### Reproduction

From `services/api`, with Java 21:

```bash
./mvnw -B test
./mvnw -B -Dtest='com.payagent.releasegates.*Test' test
```

From repo root:

```bash
node scripts/validate-fixtures.mjs
bash scripts/check-ai-slop-comments.sh
git diff --check
```

Live probes are off by default. See the README's optional integration section and the test classes for required local environment variables. Keep all JWTs, credentials and original webhook proof files out of Git. `DeployedAppReleaseGateIntegrationTest` can open a dispute and attempt acceptance: run only against a disposable confirmed Sandbox deal. Do not interpret skipped tests as completed requirements.

### Recording and submission assets

See [Member F package](member-f/README.md). The included 1920×1080, approximately 2:40 narrated video is a **storyboard rehearsal**, visibly labelled throughout; it is not actual Buyer/Seller/Sandbox footage. Public YouTube upload and Devpost submission remain outstanding. The organizer's event URL/rules were not provided, so dates, categories and eligibility in the supplied plan are not independently verified.

The ten canonical fixtures existed before this work. F validated them and corrected the agreement's canonical SHA-256 hash; synthetic evidence digests and PayPal IDs remain clearly synthetic. The plan mentions RG-1…RG-6 without individually defining them; the tracker uses proposed definitions that require team confirmation.

---

## 1. Assets & Sensitivity Classification

| Asset | Description | Sensitivity | Owner |
|-------|-------------|-------------|-------|
| **User credentials** | Email, BCrypt password hash | HIGH | Member A |
| **JWT signing secret** | HS256 key (min 32 chars) | CRITICAL | Member E (config) |
| **PayPal Sandbox credentials** | Client ID, Secret, Webhook ID | HIGH | Member B |
| **Buyer Mandate** | `limitPriceMinorUnits`, `targetPriceMinorUnits`, delivery/revision limits | HIGH | Member A |
| **Seller Mandate** | `limitPriceMinorUnits` (floor), `targetPriceMinorUnits`, delivery/revision limits | HIGH | Member A |
| **Agreement terms** | `StructuredTermsDTO` (price, deliverables, timeline) | HIGH | Member A |
| **Agreement hash** | `termsHashSha256` (RFC-8785 SHA-256) | HIGH | Member A |
| **PayPal Invoice ID** | `INV2-...` (commercial record) | MEDIUM | Member B |
| **PayPal Authorization ID** | `AUTH-...` (funds hold, 29-day expiry) | HIGH | Member B |
| **PayPal Capture ID** | `CAP-...` (completed payment) | HIGH | Member B |
| **Evidence files** | Deliverable uploads (ZIP/PDF/PNG), SHA-256 hashes | HIGH | Member E |
| **Evidence HMAC secret** | Signing key for download URLs | CRITICAL | Member E |
| **Webhook payloads** | PayPal event JSON (may contain PII) | MEDIUM | Member B |
| **Audit events** | Sequenced deal events for replay | MEDIUM | Member E |

---

## 2. Trust Boundaries

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           EXTERNAL THREAT ACTORS                            │
│  • Malicious Buyer/Seller (authenticated but adversarial)                   │
│  • Network attacker (MITM on unencrypted channels)                          │
│  • Compromised LLM endpoint (prompt injection, data exfiltration)           │
│  • Malicious PayPal webhook sender (forged signatures)                      │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         TRUST BOUNDARY: NETWORK EDGE                        │
│  TLS 1.2+ enforced • Rate limiting • WAF (future)                           │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
          ┌─────────────────────────┼─────────────────────────┐
          ▼                         ▼                         ▼
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│   WEB TIER      │       │   API TIER      │       │   DATA TIER     │
│  (Next.js)      │       │  (Spring Boot)  │       │  (PostgreSQL)   │
│  Member D       │       │  Members A,B,C,E│       │  Member A       │
└────────┬────────┘       └────────┬────────┘       └────────┬────────┘
         │                         │                         │
         │         ┌───────────────┼───────────────┐        │
         │         ▼               ▼               ▼        │
         │  ┌───────────┐   ┌─────────────┐  ┌───────────┐  │
         └──│  PAYPAL   │   │   LLM       │  │  FILE     │──┘
            │  SANDBOX  │   │  ENDPOINT   │  │  STORAGE  │
            │ (External)│   │ (External)  │  │ (Local/S3)│
            └───────────┘   └─────────────┘  └───────────┘
```

---

## 3. Threats, Mitigations & Residual Risks

### 3.1 Authentication & Authorization

| Threat ID | Threat | Mitigation | Residual Risk |
|-----------|--------|------------|---------------|
| **T-AUTH-01** | Credential stuffing / brute force on login | BCrypt (cost 10+), rate limit `/auth/login`, account lockout after 5 failures | Low — requires credentials |
| **T-AUTH-02** | JWT theft (XSS, MITM) | HttpOnly Secure SameSite=Strict cookies (planned), short expiry (15min access + refresh), TLS only | Medium — depends on cookie impl |
| **T-AUTH-03** | JWT algorithm confusion (none/HS256) | Hard-coded HS256 in `JwtTokenProvider`, reject `alg=none` | Low |
| **T-AUTH-04** | Cross-account deal access (IDOR) | **Object-level authorization**: every service call checks `deal.membership(currentUser)` → 403 if not participant | Low — enforced in service layer |
| **T-AUTH-05** | Mandate leakage to counterparty | `DealWorkspaceDTO.myMandate` scoped to authenticated role; WebSocket masks counterparty fields | Low — design-enforced |
| **T-AUTH-06** | Privilege escalation via role confusion | `PartyRole` enum only `BUYER`/`SELLER`; no admin roles in domain; all checks use `myRole` | Low |

---

### 3.2 PayPal Integration

| Threat ID | Threat | Mitigation | Residual Risk |
|-----------|--------|------------|---------------|
| **T-PP-01** | Production PayPal endpoint used accidentally | `PayPalEnvironmentGuard` fails fast if base URL ≠ `sandbox.paypal.com` | **Zero** — startup guard |
| **T-PP-02** | PayPal credentials leaked in logs/code | All creds from env vars; `.env` gitignored; no logging of tokens/secrets | Low — depends on log hygiene |
| **T-PP-03** | Forged webhook triggers false state transition | **Mandatory signature verification** via `POST /v1/notifications/verify-webhook-signature`; reject if invalid | Low — crypto-verified |
| **T-PP-04** | Duplicate webhook causes double capture/refund | `paypal_events.event_id` PRIMARY KEY; deduplication check before processing | Low — DB-enforced |
| **T-PP-05** | Authorization expiry before capture (29 days) | `authorizationExpiresAt` tracked; reconciliation job for `UNKNOWN` states; UI shows countdown | Medium — requires monitoring |
| **T-PP-06** | Invoice amount mismatch vs agreement | Invoice `detail.note` contains `termsHashSha256`; amount from `AgreementSnapshotDTO`; validated on create | Low — data integrity |

---

### 3.3 AI / LLM Integration

| Threat ID | Threat | Mitigation | Residual Risk |
|-----------|--------|------------|---------------|
| **T-AI-01** | Prompt injection leaks mandate limits | Counterparty text wrapped in `<untrusted_counterparty_data>...</untrusted_counterparty_data>`; system prompt forbids emitting mandate fields | Medium — depends on model compliance |
| **T-AI-02** | LLM overrides mandate limits in counteroffer | **All LLM outputs validated** by `MandateService` + `UtilityCalculator` before persistence; clamp/reject non-compliant | Low — hard enforcement |
| **T-AI-03** | LLM mutates database directly | **Never** — LLM returns proposed DTOs only; service layer validates & persists | Zero — architecture-enforced |
| **T-AI-04** | LLM receives PayPal credentials | **Never** — credentials not in context; AI service has no PayPal client access | Zero — architecture-enforced |
| **T-AI-05** | Model timeout / unavailability stalls negotiation | Deterministic fallback: concession curve (e.g., split difference) after 6s timeout | Low — graceful degradation |
| **T-AI-06** | PII in intake brief sent to LLM | Intake brief user-provided; no automatic PII scrubbing (user responsibility) | Medium — documented limitation |

---

### 3.4 Data Integrity & Financial Correctness

| Threat ID | Threat | Mitigation | Residual Risk |
|-----------|--------|------------|---------------|
| **T-DATA-01** | Floating-point money rounding errors | **All money = integer minor units (cents)** — `long` in Java, `number` in TypeScript; `BigDecimal` for utility math | Zero — type-enforced |
| **T-DATA-02** | Agreement terms modified after acceptance | `termsHashSha256` = SHA-256(RFC-8785 canonical JSON); any edit → new version, new hash, prior acceptances cleared | Low — crypto-enforced |
| **T-DATA-03** | Stale acceptance hash accepted | `POST /acceptance` requires exact `termsHashSha256` match; 409 Conflict on mismatch | Zero — API-enforced |
| **T-DATA-04** | Idempotency key replay with different payload | `idempotency_keys` stores `request_hash` (SHA-256 of canonical request); mismatch → 409 Conflict | Low — hash-enforced |
| **T-DATA-05** | Evidence file tampering after upload | SHA-256 computed on upload stream; stored in DB; verified on download | Low — integrity-enforced |
| **T-DATA-06** | Evidence accessed by non-participant | Download URL signed with HMAC (15-min TTL); endpoint verifies deal membership | Low — auth-enforced |

---

### 3.5 Dispute & Settlement Freeze

| Threat ID | Threat | Mitigation | Residual Risk |
|-----------|--------|------------|---------------|
| **T-DISP-01** | Automated capture proceeds after dispute opened | `POST /disputes` → immediate `DealStatus=DISPUTED`; `SettlementService.freezeCapture(dealId)` called; all capture jobs check status | Low — state-enforced |
| **T-DISP-02** | Dispute resolution bypasses freeze | Resolution endpoints (`RESOLVED_RELEASE_CAPTURE` / `RESOLVED_VOID_AUTHORIZATION`) require mutual consent or admin; audit logged | Low — process-enforced |
| **T-DISP-03** | Seller uploads malware as deliverable | MIME allowlist (`.zip`, `.pdf`, `.png`); max 10MB; stored outside web root; scanned on download (future) | Medium — AV scanning not yet implemented |

---

### 3.6 Infrastructure & Operations

| Threat ID | Threat | Mitigation | Residual Risk |
|-----------|--------|------------|---------------|
| **T-OPS-01** | Secrets committed to Git | `.gitignore` excludes `.env`, `.env.*`; `gitleaks` in CI; pre-commit hook (recommended) | Low — tool-enforced |
| **T-OPS-02** | Docker image contains secrets | Multi-stage build; secrets only at runtime via env; no `ARG` for secrets | Low |
| **T-OPS-03** | CI logs leak secrets | GitHub Actions `permissions: contents: read`; no `echo $SECRET`; masked in logs | Low |
| **T-OPS-04** | Dependency vulnerability | `dependabot` alerts (recommended); pinned action SHAs in CI; `npm audit` / `mvn dependency-check` (future) | Medium — not yet automated |
| **T-OPS-05** | Local dev uses production PayPal creds | `.env.example` has empty placeholders; `PayPalEnvironmentGuard` blocks non-sandbox | **Zero** — guard-enforced |

---

## 4. Security Invariants (Non-Negotiable Checklist)

*From `AGENTS.md` §4 — must hold at all times:*

1. ☐ A party's `MandateDTO` is **never** serialized to the counterparty in REST or WebSocket payloads.
2. ☐ Every deal, mandate, evidence, settlement access is checked against authenticated deal membership and role. Cross-account → `403`.
3. ☐ JWT signing key, PayPal client ID/secret, webhook ID, evidence HMAC secret — all from environment variables. `.env` never committed.
4. ☐ `PayPalEnvironmentGuard` fails fast if `payagent.paypal.base-url` does not contain `sandbox.paypal.com`.
5. ☐ All PayPal webhooks verified via `POST /v1/notifications/verify-webhook-signature` before processing. Event deduplication via `paypal_events.event_id` PK.
6. ☐ All mutating endpoints require `Idempotency-Key` header. Stored in `idempotency_keys` with request hash.
7. ☐ **Money = integer minor units (cents).** `priceMinorUnits`, `limitPriceMinorUnits`, `amountMinorUnits` are `long`/`number`. **Never** floating-point.
8. ☐ LLM never receives PayPal credentials, never computes authoritative utility scores, never mutates DB directly. All LLM outputs are proposed DTOs validated by `MandateService` and `UtilityCalculator`.
9. ☐ Counterparty text wrapped in `<untrusted_counterparty_data>...</untrusted_counterparty_data>` XML delimiters. Output schema validated.
10. ☐ `termsHashSha256` = SHA-256 of RFC-8785 canonical JSON of `canonicalTerms`. Any modification → new version, new hash, prior acceptances cleared.
11. ☐ Creating a dispute (`POST /disputes`) immediately transitions `DealStatus` → `DISPUTED` and blocks automated capture.
12. ☐ Evidence files stored outside web root, SHA-256 hashed on upload, accessed via 15-min HMAC-signed URLs verified by deal membership.
13. ☐ PayPal webhook `UNKNOWN` states trigger reconciliation job; no silent success assumptions.

---

## 5. Test Coverage for Security (Release Gates TST-01..TST-15)

| Test ID | Security Property | Status |
|---------|-------------------|--------|
| **TST-01** | Mandate cap boundary (1 cent over → reject) | ❌ Not implemented |
| **TST-02** | Agreement v1 hash invalidation on v2 edit | ❌ Not implemented |
| **TST-03** | Prompt injection → no mandate leaks | ❌ Not implemented |
| **TST-04** | Prompt injection → no limit overrides | ❌ Not implemented |
| **TST-05** | LLM timeout → deterministic fallback | ❌ Not implemented |
| **TST-06** | LLM schema violation → reject/clamp | ❌ Not implemented |
| **TST-07** | Forged webhook → rejected (400) | ❌ Not implemented |
| **TST-08** | Duplicate webhook → deduplicated | ❌ Not implemented |
| **TST-09** | Idempotency replay with different payload → 409 | ❌ Not implemented |
| **TST-10** | Cross-account IDOR on deal/mandate/evidence → 403 | ❌ Not implemented |
| **TST-11** | Dispute creation → capture freeze | ❌ Not implemented |
| **TST-12** | Evidence download without membership → 403 | ❌ Not implemented |
| **TST-13** | Evidence HMAC expiry → 403 after 15 min | ❌ Not implemented |
| **TST-14** | JWT expiry → 401 on protected endpoints | ❌ Not implemented |
| **TST-15** | PayPal `UNKNOWN` state → reconciliation triggered | ❌ Not implemented |

**Owner:** Member F (implements in `services/api/src/test/java/com/payagent/releasegates/`)

---

## 6. Incident Response Playbook (High-Level)

| Scenario | Detection | Containment | Recovery |
|----------|-----------|-------------|----------|
| PayPal credentials leaked | Gitleaks alert / manual review | Rotate credentials in PayPal Developer Dashboard; update `.env` | Deploy new credentials; audit access logs |
| JWT secret compromised | Unusual auth patterns / audit | Rotate `PAYAGENT_JWT_SECRET`; invalidate all sessions | Deploy new secret; force re-login |
| Evidence HMAC secret leaked | Unauthorized downloads detected | Rotate `PAYAGENT_EVIDENCE_SIGNING_SECRET` | Re-sign all active evidence URLs |
| LLM prompt injection successful | Mandate limits overridden in offer | Disable AI negotiation (`AUTONOMOUS_BOUNDED` → `ASSISTED`); investigate | Patch `PromptSanitizer`; re-enable |
| Database credential leaked | Connection anomalies | Rotate DB password; update Docker Compose / `.env` | Redeploy stack |

---

## 7. Compliance & Standards Alignment

| Standard | Applicability | Status |
|----------|---------------|--------|
| **OWASP ASVS 4.0 Level 2** | Authentication, session mgmt, access control, input validation | Planned — not verified |
| **PCI DSS SAQ A** | PayPal handles card data; we only process Sandbox tokens | Out of scope (Sandbox only) |
| **GDPR** | Email addresses, potential PII in intake briefs | Data minimization; deletion on account closure (planned) |
| **SOC 2 Type II** | Not applicable (hackathon project) | N/A |

---

## 8. Current Implementation Status

| Security Control | Implemented | Planned / Owner |
|------------------|-------------|-----------------|
| `PayPalEnvironmentGuard` | ✅ Startup sandbox check | — |
| BCrypt password hashing | ✅ Dependency in pom.xml | `UserService` impl (Member A) |
| JWT HS256 | ✅ Config property | `JwtTokenProvider` + filter (Member A) |
| Object-level auth (deal guard) | ❌ | Member A |
| Mandate privacy (API) | ❌ | Member A |
| Mandate privacy (WebSocket) | ❌ | Member E |
| Idempotency keys table | ✅ Schema (`V1__init.sql`) | Service impl (Member A) |
| Idempotency filter/interceptor | ❌ | Member A |
| PayPal webhook verification | ❌ | Member B |
| Webhook deduplication | ✅ Schema (`paypal_events`) | Controller impl (Member B) |
| Evidence SHA-256 on upload | ❌ | Member E |
| Evidence HMAC signed URLs | ❌ | Member E |
| Dispute freeze capture | ❌ | Member E |
| Agreement RFC-8785 hash | ❌ | Member A |
| UtilityCalculator (BigDecimal) | ❌ | Member A |
| PromptSanitizer | ❌ | Member C |
| Gitleaks in CI | ✅ Workflow configured | Requires Gitleaks installed |
| AI-slop comment check | ✅ Script + CI job | — |

---

## 9. Residual Risks (Accepted for Hackathon Scope)

| Risk | Reason | Mitigation |
|------|--------|------------|
| No AV scanning on evidence uploads | Time constraint | MIME allowlist + size limit + external storage |
| No rate limiting on API endpoints | Time constraint | Documented; future: Spring Boot actuator + Redis |
| No WAF / DDoS protection | Infrastructure scope | Deploy behind Cloudflare/NGINX in production |
| No automated dependency scanning | Time constraint | Manual `npm audit` / `mvn dependency-check` before freeze |
| Short JWT expiry may impact UX | Security priority | 15min access + refresh token pattern (planned) |
| No audit log immutability guarantee | PostgreSQL only | `audit_events` append-only; future: WORM storage |

---

## 10. Verification Checklist (Pre-Freeze)

- [ ] All 13 security invariants (Section 4) verified in code review
- [ ] TST-01..TST-15 passing in CI
- [ ] `gitleaks detect --source .` — zero findings
- [ ] `bash scripts/check-ai-slop-comments.sh` — clean
- [ ] No `.env`, `.env.*`, or secrets in Git history (`git log --all --full-history -- .env`)
- [ ] `PayPalEnvironmentGuard` tested with non-sandbox URL → startup failure
- [ ] Cross-account access attempts return 403 (manual test)
- [ ] Dispute creation blocks capture in integration test
- [ ] Evidence download without valid HMAC → 403
- [ ] Agreement hash changes on 1-cent edit → prior acceptances cleared