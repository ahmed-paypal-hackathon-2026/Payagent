# PAYAGENT — AGENTS.md

**Authoritative repository-wide rules for all AI agents (Cursor, Claude Code, Codex, OpenCode).**  
Source of truth: `plan v2.docx` (extracted to `docs/_plan_extract.txt`).  
Last synced: 2026-10-22.

---

## 1. Architecture & Stack (Exact Versions)

| Layer | Technology | Version | Owner |
|-------|------------|---------|-------|
| Frontend | Next.js (App Router) + React + TypeScript + Tailwind CSS | 14.2.x / 18 / 5 / 3.4.x | Member D |
| Backend | Spring Boot + Java + Maven | 3.3.x / 21 / 3.9+ | Member A, B, C, E |
| Database | PostgreSQL | 16 | Member E (infra) / Member A (schema) |
| AI | Spring AI (OpenAI-compatible) | 1.0.x | Member C |
| PayPal | Sandbox only — Invoicing v2, Orders v2, Payments v2, Webhooks | — | Member B |
| Realtime | Spring WebSocket STOMP | — | Member E |
| Build | Maven Wrapper (`services/api/mvnw`), npm (`apps/web/`) | — | Member E |
| CI | GitHub Actions (pinned SHAs) | — | Member E |
| License | Apache-2.0 | — | Member E |

**Monorepo layout:**
```
Payagent/
├── apps/web/                 # Next.js 14 (Member D)
├── services/api/             # Spring Boot 3.3 (Members A, B, C, E)
│   └── src/main/java/com/payagent/
│       ├── common/contracts/     # Member A — Canonical DTOs & Enums
│       ├── auth/                 # Member A — JWT, login, deal guard
│       ├── deals/                # Member A — Deal lifecycle, offers
│       ├── mandates/             # Member A — Versioned mandates, utility math
│       ├── agreements/           # Member A — Agreement hash, dual consent
│       ├── paypal/               # Member B — OAuth, Invoicing, Orders, Webhooks
│       ├── settlement/           # Member B — Dual-rail settlement flow
│       ├── ai/                   # Member C — Intake extraction, counteroffers, sanitizer
│       ├── evidence/             # Member E — SHA-256 storage, signed URLs
│       ├── disputes/             # Member E — Dispute freeze, resolution
│       └── realtime/             # Member E — WebSocket STOMP, cursor replay
├── shared/fixtures/          # Member F — 10 canonical JSON fixtures
├── docs/                     # Architecture, security, local dev, agent setup, handoff
├── .github/workflows/ci.yml  # Member E — CI pipeline
├── docker-compose.yml        # Member E — Postgres + API + Web
├── render.yaml               # Member E — Cloud blueprint
├── AGENTS.md                 # This file
├── CLAUDE.md                 # Claude Code entry point
├── README.md                 # Member F — Onboarding guide
├── LICENSE                   # Apache-2.0
├── .env.example              # Safe placeholders only
└── .gitignore                # Excludes secrets, builds, IDE, evidence
```

---

## 2. Six-Person Ownership Boundaries (Strict)

| Member | Owned Paths | Must NOT Touch |
|--------|-------------|----------------|
| **A** — Domain, Contracts, Mandates, State Machine | `services/api/src/main/java/com/payagent/common/contracts/**`, `auth/**`, `deals/**`, `mandates/**`, `agreements/**`, `db/migration/**` | `paypal/**`, `ai/**`, `apps/web/**` |
| **B** — PayPal Sandbox & Settlement | `services/api/src/main/java/com/payagent/paypal/**`, `settlement/**` | `ai/**`, `apps/web/**`, `mandates/**` (utility math) |
| **C** — Spring AI & Negotiation Agent | `services/api/src/main/java/com/payagent/ai/**`, `docs/AI_BENCHMARK.md` | `paypal/**`, `apps/web/**`, DB mutations, mandate decisions |
| **D** — Web Workspace (Next.js) | `apps/web/**` | Backend domain logic, PayPal credentials, authoritative financial status |
| **E** — Infra, Security, Realtime, Evidence | `LICENSE`, `.gitignore`, `.env.example`, `docker-compose.yml`, `render.yaml`, `.github/workflows/ci.yml`, `evidence/**`, `disputes/**`, `realtime/**` | `paypal/**`, `ai/**` |
| **F** — Fixtures, QA, Release Gates, Docs | `shared/fixtures/**`, `services/api/src/test/java/com/payagent/releasegates/**`, `README.md`, `docs/SECURITY_THREAT_MODEL.md` | Other members' implementation paths |

**Coordination rule:** Shared contract changes (Java DTOs/Enums ↔ TypeScript) require synchronized PRs from Member A and Member D. No silent cross-boundary edits.

---

## 3. Canonical Contracts (Single Source of Truth)

**Java (Member A):** `com.payagent.common.contracts` — 7 enums + 7 records  
**TypeScript (Member D):** `apps/web/src/types/contracts.ts` — exact mirror

### Enums (must match 1:1)
- `PartyRole` — `BUYER` | `SELLER`
- `NegotiationMode` — `ASSISTED` | `AUTONOMOUS_BOUNDED`
- `DealStatus` — `DRAFT` | `INVITED` | `ACTIVE_NEGOTIATION` | `TERMS_PENDING_ACCEPTANCE` | `AGREED` | `SETTLEMENT_PENDING` | `IN_PROGRESS` | `DELIVERABLES_SUBMITTED` | `ACCEPTED` | `DISPUTED` | `CLOSED` | `CANCELLED`
- `NegotiationStopReason` — `NONE` | `CONVERGED` | `NO_ZOPA_DETECTED` | `MAX_ROUNDS_REACHED` | `MANDATE_EXPIRED` | `USER_PAUSED` | `POLICY_VIOLATION_BLOCKED`
- `InvoiceLifecycleStatus` — `NOT_CREATED` | `DRAFT` | `SENT` | `PAID` | `CANCELLED` | `UNKNOWN`
- `PaymentLifecycleStatus` — `NOT_STARTED` | `ORDER_CREATED` | `AUTHORIZED` | `CAPTURE_PENDING` | `COMPLETED` | `VOIDED` | `DECLINED` | `UNKNOWN`
- `DisputeStatus` — `NONE` | `OPEN` | `UNDER_PARTIES_REVIEW` | `RESOLVED_RELEASE_CAPTURE` | `RESOLVED_VOID_AUTHORIZATION`

### DTOs (must match 1:1)
- `StructuredTermsDTO` — title, deliverables[], priceMinorUnits (long, cents), currency, deliveryDays, includedRevisions, acceptanceCriteriaText
- `MandateDTO` — mandateId, dealId, partyRole, version, currency, limitPriceMinorUnits, targetPriceMinorUnits, limitDeliveryDays, targetDeliveryDays, limitRevisions, targetRevisions, mustHaveDeliverables[], forbiddenTerms[], mode, maxRounds, expiresAt
- `OfferDTO` — offerId, dealId, roundNumber, proposerRole, generatedByAgent, terms, rationaleSummary, callerUtilityScore (BigDecimal string), mandateCompliant, createdAt
- `AgreementSnapshotDTO` — agreementId, dealId, version, canonicalTerms, termsHashSha256 (RFC-8785 SHA-256), buyerAcceptedAt, sellerAcceptedAt
- `SettlementStateDTO` — dealId, agreementVersion, invoiceStatus, paypalInvoiceId, paypalInvoiceNumber, paypalInvoiceViewUrl, paymentStatus, paypalOrderId, paypalAuthorizationId, paypalCaptureId, authorizationExpiresAt, amountMinorUnits, currency, lastReconciledAt
- `DeliverableEvidenceDTO` — evidenceId, dealId, uploaderRole, filename, mimeType, sizeBytes, sha256Hash, version, downloadSignedUrl, uploadedAt
- `DealWorkspaceDTO` — dealId, status, stopReason, buyerEmail, sellerEmail, myRole, myMandate (never counterparty's), offers[], currentAgreement, settlement, evidenceList[], disputeStatus, disputeReason

**Parity test:** `ContractTypeScriptParityTest.java` validates enum literal equality. Run before merging contract changes.

---

## 4. Security Invariants (Non-Negotiable)

1. **Mandate privacy:** A party's `MandateDTO` is **never** serialized to the counterparty in REST or WebSocket payloads. `DealWorkspaceDTO.myMandate` is scoped to the authenticated user only.
2. **Object-level authorization:** Every deal, mandate, evidence, settlement access is checked against authenticated deal membership and role. Cross-account access → `403 Forbidden`.
3. **No hard-coded secrets:** JWT signing key, PayPal client ID/secret, webhook ID, evidence HMAC secret — all from environment variables. `.env` never committed.
4. **PayPal Sandbox only:** `PayPalEnvironmentGuard` fails fast if `payagent.paypal.base-url` does not contain `sandbox.paypal.com`. Production endpoints blocked.
5. **Webhook verification:** All PayPal webhooks verified via `POST /v1/notifications/verify-webhook-signature` before processing. Event deduplication via `paypal_events.event_id` PK.
6. **Idempotency:** All mutating endpoints require `Idempotency-Key` header. Stored in `idempotency_keys` table with request hash.
7. **Money = integer minor units:** `priceMinorUnits`, `limitPriceMinorUnits`, `amountMinorUnits` are `long` (cents). **Never** floating-point.
8. **LLM trust boundary:** LLM never receives PayPal credentials, never computes authoritative utility scores, never mutates DB directly. All LLM outputs are proposed DTOs validated by Member A's `MandateService` and `UtilityCalculator`.
8. **Prompt injection defense:** Counterparty text wrapped in `<untrusted_counterparty_data>...</untrusted_counterparty_data>` XML delimiters. Output schema validated.
9. **Agreement hash integrity:** `termsHashSha256` = SHA-256 of RFC-8785 canonical JSON of `canonicalTerms`. Any modification → new version, new hash, prior acceptances cleared.
10. **Dispute freeze:** Creating a dispute (`POST /api/v1/deals/{id}/disputes`) immediately transitions `DealStatus` → `DISPUTED` and blocks automated capture.
11. **Evidence privacy:** Files stored outside web root, SHA-256 hashed on upload, accessed via 15-min HMAC-signed URLs verified by deal membership.
12. **UNKNOWN-state reconciliation:** PayPal webhook `UNKNOWN` states trigger reconciliation job; no silent success assumptions.

---

## 5. Development Commands (Verified)

### Backend (from `services/api/`)
```bash
# Run tests (H2 profile, no DB needed)
./mvnw -B test

# Package (requires DB for Flyway validate; use test profile or docker)
./mvnw -B -DskipTests package

# Run locally with Docker Compose (from repo root)
docker compose up --build -d
# API at http://localhost:8080, health: /actuator/health
```

### Frontend (from `apps/web/`)
```bash
# Install deps (uses package-lock.json)
npm ci

# TypeScript check
npm run typecheck

# Lint
npm run lint

# Dev server (mock mode by default)
npm run dev
# Web at http://localhost:3000

# Production build
npm run build
```

### Fixtures Validation
```bash
# From repo root
node scripts/validate-fixtures.mjs
```

### Docker Compose (from repo root)
```bash
# Validate config
docker compose -f docker-compose.yml config

# Start stack
docker compose up --build -d

# Stop
docker compose down -v
```

### Secret Scanning
```bash
gitleaks detect --source . --verbose
```

### AI-Slop Comment Check
```bash
bash scripts/check-ai-slop-comments.sh
```

---

## 6. Agent Rules (Minimal Diffs, No Overwrites)

- **Read before write:** Always inspect target file and `git status` before editing.
- **Preserve existing work:** Never overwrite teammate's implementation. Coordinate on shared contracts.
- **No fake implementations:** Do not return hard-coded success for security/financial operations. Use proper error types.
- **Report actual verification:** Distinguish PASS/FAIL/BLOCKED/NOT RUN. Do not claim success by inference.
- **Secrets hygiene:** Never print `.env` values. Never commit `.env` or real credentials.
- **Sandbox only:** No production PayPal calls. No real-money transactions.
- **Contract changes:** Update both Java and TypeScript in same PR. Run parity test.
- **Test first:** New backend logic → unit test. New frontend component → typecheck + lint pass.

---

## 7. Environment Variables (from `.env.example`)

| Variable | Description | Required |
|----------|-------------|----------|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT` | PostgreSQL connection | Yes (Docker) |
| `PAYAGENT_DB_URL`, `PAYAGENT_DB_USER`, `PAYAGENT_DB_PASSWORD` | Spring datasource | Yes |
| `PAYAGENT_JWT_SECRET` | HS256 signing key (min 32 chars) | Yes |
| `PAYPAL_API_BASE_URL` | Must be `https://api-m.sandbox.paypal.com` | Yes (Member B) |
| `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET` | Sandbox app credentials | Yes (Member B) |
| `PAYPAL_WEBHOOK_ID` | Webhook ID for verification | Yes (Member B) |
| `SPRING_AI_OPENAI_API_KEY`, `SPRING_AI_OPENAI_BASE_URL`, `SPRING_AI_OPENAI_MODEL` | LLM config | Member C |
| `PAYAGENT_EVIDENCE_STORAGE_PATH`, `PAYAGENT_EVIDENCE_SIGNING_SECRET` | Evidence storage | Member E |
| `NEXT_PUBLIC_API_BASE_URL`, `NEXT_PUBLIC_USE_MOCKS`, `WEB_PORT` | Frontend config | Member D |

**Setup:** `copy .env.example .env` (Windows) / `cp .env.example .env` (Unix), then fill in secrets locally.

---

## 8. Deadlines (Africa/Cairo Time)

- **Hard Internal Freeze:** 2026-11-11 22:00 — Feature complete, demo recorded, Devpost submitted
- **Official Deadline:** 2026-11-12 22:00 — Final submission

---

## 9. Verification Checklist (Run Before Handoff)

- [ ] `git status --short` clean (no unintended changes)
- [ ] `git diff --check` passes
- [ ] `./mvnw -B test` (backend) — PASS
- [ ] `npm ci && npm run lint && npm run typecheck && npm run build` (frontend) — PASS
- [ ] `node scripts/validate-fixtures.mjs` — PASS
- [ ] `docker compose config` — PASS
- [ ] `gitleaks detect --source .` — PASS (no secrets)
- [ ] `bash scripts/check-ai-slop-comments.sh` — PASS
- [ ] Contract parity test — PASS
- [ ] `AGENTS.md`, `CLAUDE.md`, Cursor rules, OpenCode skills present and consistent

---

*This file is the single source of truth for agent behavior. All tool-specific configs (Cursor, Claude Code, Codex, OpenCode) reference this file.*