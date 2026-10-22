# Payagent — Security Threat Model

**Source of truth:** `plan v2.docx` (extracted to `docs/_plan_extract.txt`), `AGENTS.md` §4, `.cursor/rules/security.mdc`.  
**Status:** Documents *planned* security invariants and mitigations. Implementation is in scaffolding phase.

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