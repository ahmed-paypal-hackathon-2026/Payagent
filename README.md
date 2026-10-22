# Payagent

**Autonomous Bilateral Negotiating Agent with PayPal Sandbox Settlement**

Hackathon project for a 6-person team. Built with Next.js 14, Spring Boot 3.3, PostgreSQL 16, and PayPal Sandbox.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        NEXT.JS 14 WEB APP (apps/web)                │
│  Member D  ──────────────────────────────────────────────────────  │
│  Buyer Workspace  •  Seller Workspace  •  Deal Room  •  Audit UI   │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ REST + Idempotency-Key
                           │ WSS /ws/deals/{id} (STOMP)
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  SPRING BOOT 3.3 MODULAR MONOLITH                   │
│  services/api  (Members A, B, C, E)                                 │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐   │
│  │ Domain Core │ │ PayPal Adpt │ │ AI Engine   │ │ Evidence/   │   │
│  │ (Member A)  │ │ (Member B)  │ │ (Member C)  │ │ Realtime    │   │
│  │             │ │             │ │             │ │ (Member E)  │   │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘   │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ JDBC / Flyway
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    POSTGRESQL 16 (Docker Compose)                   │
│  users, deals, mandates, offers, agreements, invoices, payments,   │
│  evidence, disputes, paypal_events, audit_events, idempotency_keys │
└─────────────────────────────────────────────────────────────────────┘
                           ▲
                           │ HTTPS (Sandbox only)
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  EXTERNAL: PAYPAL SANDBOX CLOUD                     │
│  api-m.sandbox.paypal.com                                           │
│  OAuth2 • Invoicing v2 • Orders v2 (AUTHORIZE) • Payments v2       │
│  Webhook Verification                                               │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Technology Stack

| Layer | Technology | Version |
|-------|------------|---------|
| Frontend | Next.js (App Router) + React + TypeScript + Tailwind CSS | 14.2.x / 18 / 5 / 3.4.x |
| Backend | Spring Boot + Java + Maven | 3.3.x / 21 / 3.9+ (wrapper) |
| Database | PostgreSQL | 16 |
| AI | Spring AI (OpenAI-compatible) | 1.0.x |
| PayPal | Sandbox only — Invoicing v2, Orders v2, Payments v2, Webhooks | — |
| Realtime | Spring WebSocket STOMP | — |
| CI/CD | GitHub Actions (pinned SHAs) | — |
| License | Apache-2.0 | — |

---

## Directory Layout

```
Payagent/
├── apps/web/                     # Next.js 14 SPA (Member D)
├── services/api/                 # Spring Boot 3.3 (Members A, B, C, E)
│   └── src/main/java/com/payagent/
│       ├── common/contracts/     # Canonical DTOs & Enums (Member A)
│       ├── auth/                 # JWT, login, deal guard (Member A)
│       ├── deals/                # Deal lifecycle, offers (Member A)
│       ├── mandates/             # Versioned mandates, utility math (Member A)
│       ├── agreements/           # Agreement hash, dual consent (Member A)
│       ├── paypal/               # OAuth, Invoicing, Orders, Webhooks (Member B)
│       ├── settlement/           # Dual-rail settlement flow (Member B)
│       ├── ai/                   # Intake extraction, counteroffers, sanitizer (Member C)
│       ├── evidence/             # SHA-256 storage, signed URLs (Member E)
│       ├── disputes/             # Dispute freeze, resolution (Member E)
│       └── realtime/             # WebSocket STOMP, cursor replay (Member E)
├── shared/fixtures/              # 10 canonical JSON fixtures (Member F)
├── docs/                         # Architecture, security, local dev, agent setup, handoff
├── .github/workflows/ci.yml      # CI pipeline (Member E)
├── docker-compose.yml            # Postgres + API + Web (Member E)
├── render.yaml                   # Cloud blueprint (Member E)
├── AGENTS.md                     # Authoritative agent instructions
├── CLAUDE.md                     # Claude Code entry point
├── LICENSE                       # Apache-2.0
├── .env.example                  # Safe placeholders only
└── .gitignore                    # Excludes secrets, builds, IDE, evidence
```

---

## Six-Person Ownership Model

| Member | Role | Owned Paths | Must NOT Touch |
|--------|------|-------------|----------------|
| **A** | Backend Domain, Contracts, Mandates, State Machine | `services/api/src/main/java/com/payagent/common/contracts/**`, `auth/**`, `deals/**`, `mandates/**`, `agreements/**`, `db/migration/**` | `paypal/**`, `ai/**`, `apps/web/**` |
| **B** | PayPal Sandbox & Settlement | `services/api/src/main/java/com/payagent/paypal/**`, `settlement/**` | `ai/**`, `apps/web/**`, `mandates/**` (utility math) |
| **C** | Spring AI & Negotiation Agent | `services/api/src/main/java/com/payagent/ai/**`, `docs/AI_BENCHMARK.md` | `paypal/**`, `apps/web/**`, DB mutations, mandate decisions |
| **D** | Web Workspace (Next.js) | `apps/web/**` | Backend domain logic, PayPal credentials, authoritative financial status |
| **E** | Infra, Security, Realtime, Evidence | `LICENSE`, `.gitignore`, `.env.example`, `docker-compose.yml`, `render.yaml`, `.github/workflows/ci.yml`, `evidence/**`, `disputes/**`, `realtime/**` | `paypal/**`, `ai/**` |
| **F** | Fixtures, QA, Release Gates, Docs | `shared/fixtures/**`, `services/api/src/test/java/com/payagent/releasegates/**`, `README.md`, `docs/SECURITY_THREAT_MODEL.md` | Other members' implementation paths |

**Coordination rule:** Shared contract changes (Java DTOs/Enums ↔ TypeScript) require synchronized PRs from Member A and Member D. No silent cross-boundary edits.

---

## Canonical Contracts (Single Source of Truth)

**Java (Member A):** `com.payagent.common.contracts` — 7 enums + 7 records
**TypeScript (Member D):** `apps/web/src/types/contracts.ts` — exact mirror

### Enums
- `PartyRole` — `BUYER` | `SELLER`
- `NegotiationMode` — `ASSISTED` | `AUTONOMOUS_BOUNDED`
- `DealStatus` — `DRAFT` | `INVITED` | `ACTIVE_NEGOTIATION` | `TERMS_PENDING_ACCEPTANCE` | `AGREED` | `SETTLEMENT_PENDING` | `IN_PROGRESS` | `DELIVERABLES_SUBMITTED` | `ACCEPTED` | `DISPUTED` | `CLOSED` | `CANCELLED`
- `NegotiationStopReason` — `NONE` | `CONVERGED` | `NO_ZOPA_DETECTED` | `MAX_ROUNDS_REACHED` | `MANDATE_EXPIRED` | `USER_PAUSED` | `POLICY_VIOLATION_BLOCKED`
- `InvoiceLifecycleStatus` — `NOT_CREATED` | `DRAFT` | `SENT` | `PAID` | `CANCELLED` | `UNKNOWN`
- `PaymentLifecycleStatus` — `NOT_STARTED` | `ORDER_CREATED` | `AUTHORIZED` | `CAPTURE_PENDING` | `COMPLETED` | `VOIDED` | `DECLINED` | `UNKNOWN`
- `DisputeStatus` — `NONE` | `OPEN` | `UNDER_PARTIES_REVIEW` | `RESOLVED_RELEASE_CAPTURE` | `RESOLVED_VOID_AUTHORIZATION`

### DTOs
- `StructuredTermsDTO` — title, deliverables[], priceMinorUnits (long, cents), currency, deliveryDays, includedRevisions, acceptanceCriteriaText
- `MandateDTO` — mandateId, dealId, partyRole, version, currency, limitPriceMinorUnits, targetPriceMinorUnits, limitDeliveryDays, targetDeliveryDays, limitRevisions, targetRevisions, mustHaveDeliverables[], forbiddenTerms[], mode, maxRounds, expiresAt
- `OfferDTO` — offerId, dealId, roundNumber, proposerRole, generatedByAgent, terms, rationaleSummary, callerUtilityScore (BigDecimal string), mandateCompliant, createdAt
- `AgreementSnapshotDTO` — agreementId, dealId, version, canonicalTerms, termsHashSha256 (RFC-8785 SHA-256), buyerAcceptedAt, sellerAcceptedAt
- `SettlementStateDTO` — dealId, agreementVersion, invoiceStatus, paypalInvoiceId, paypalInvoiceNumber, paypalInvoiceViewUrl, paymentStatus, paypalOrderId, paypalAuthorizationId, paypalCaptureId, authorizationExpiresAt, amountMinorUnits, currency, lastReconciledAt
- `DeliverableEvidenceDTO` — evidenceId, dealId, uploaderRole, filename, mimeType, sizeBytes, sha256Hash, version, downloadSignedUrl, uploadedAt
- `DealWorkspaceDTO` — dealId, status, stopReason, buyerEmail, sellerEmail, myRole, myMandate (never counterparty's), offers[], currentAgreement, settlement, evidenceList[], disputeStatus, disputeReason

**Parity test:** `ContractTypeScriptParityTest.java` validates enum literal equality. Run before merging contract changes.

---

## Prerequisites

- **Java 21** (Temurin/Eclipse)
- **Maven 3.9+** (or use wrapper `services/api/mvnw`)
- **Node.js 20** + **npm 10+**
- **Docker** + **Docker Compose v2** (for PostgreSQL + full stack)
- **Git**
- **PayPal Developer Account** (for Sandbox credentials — Member B only)
- **OpenAI-compatible LLM endpoint** (for AI features — Member C only)

---

## Fresh-Clone Setup

```bash
# 1. Clone
git clone https://github.com/mona585/Payagent.git
cd Payagent

# 2. Environment (copy and fill in secrets locally)
copy .env.example .env          # Windows
# cp .env.example .env          # Linux/macOS
# Edit .env with your local values (JWT secret, PayPal sandbox creds, etc.)

# 3. Start full stack with Docker (recommended)
docker compose up --build -d
# API:  http://localhost:8080/actuator/health
# Web:  http://localhost:3000

# 4. Or run locally without Docker
# Backend (from services/api/):
./mvnw -B test                          # Unit tests (H2 profile)
# ./mvnw -B spring-boot:run             # Requires local Postgres on localhost:5432

# Frontend (from apps/web/):
npm ci
npm run dev                             # Mock mode by default (NEXT_PUBLIC_USE_MOCKS=true)
```

---

## Environment Setup (No Secrets)

1. Copy `.env.example` to `.env` (never commit `.env`)
2. Fill in **only** the values you need locally:
   - `PAYAGENT_JWT_SECRET` — generate a 32+ char random string
   - `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`, `PAYPAL_WEBHOOK_ID` — from PayPal Developer Dashboard (Sandbox app)
   - `SPRING_AI_OPENAI_API_KEY`, `SPRING_AI_OPENAI_BASE_URL` — for AI features
3. All other variables have safe local defaults for Docker Compose

**Never** put real credentials in `.env.example`, fixtures, docs, or CI logs.

---

## Docker Compose Startup & Shutdown

```bash
# From repo root
docker compose up --build -d      # Start all services (Postgres, API, Web)
docker compose ps                 # Check status
docker compose logs -f api        # Follow API logs
docker compose logs -f web        # Follow Web logs
docker compose down               # Stop (keeps volumes)
docker compose down -v            # Stop + remove volumes (clean slate)
```

**Ports:**
- PostgreSQL: `5432` (host) → `5432` (container)
- API: `8080` (host) → `8080` (container)
- Web: `3000` (host) → `3000` (container)

**Health checks:**
- API: `GET http://localhost:8080/actuator/health`
- Web: `GET http://localhost:3000`

---

## Local Development Commands

### Backend (`services/api/`)
```bash
./mvnw -B test                    # All tests (H2 profile, no DB needed)
./mvnw -B -DskipTests package     # Package JAR
./mvnw -B test -Dtest=HealthControllerTest
./mvnw -B test -Dtest=ContractTypeScriptParityTest
./mvnw -B test -Dtest=ContractFixtureDeserializationTest
```

### Frontend (`apps/web/`)
```bash
npm ci                            # Install (uses package-lock.json)
npm run dev                       # Dev server at http://localhost:3000 (mock mode)
npm run typecheck                 # tsc --noEmit
npm run lint                      # next lint
npm run build                     # Production build (standalone output)
npm run start                     # Run production build
```

### Fixtures Validation (repo root)
```bash
node scripts/validate-fixtures.mjs
```

### Security Checks (repo root)
```bash
gitleaks detect --source . --verbose          # Secret scan
bash scripts/check-ai-slop-comments.sh        # Placeholder comment check
```

---

## Test Commands

| Layer | Command | Status |
|-------|---------|--------|
| Backend unit tests | `./mvnw -B test` | **BLOCKED** — Maven wrapper download fails (network) |
| Backend package | `./mvnw -B -DskipTests package` | **BLOCKED** — same |
| Frontend typecheck | `npm run typecheck` | **PASS** |
| Frontend lint | `npm run lint` | **PASS** |
| Frontend build | `npm run build` | **PASS** |
| Fixtures validation | `node scripts/validate-fixtures.mjs` | **PASS** (10/10 OK) |
| Docker Compose config | `docker compose config` | **BLOCKED** — Docker not installed |
| Secret scan | `gitleaks detect` | **BLOCKED** — Gitleaks not installed |
| AI-slop check | `bash scripts/check-ai-slop-comments.sh` | **BLOCKED** — Bash/WSL unavailable; `git grep` clean |

---

## API Health-Check

```bash
# With Docker Compose running
curl -f http://localhost:8080/actuator/health
# Expected: {"status":"UP",...}

curl -f http://localhost:8080/api/v1/health
# Expected: {"status":"UP","service":"payagent-api"}
```

---

## Agent Setup Instructions

| Tool | Config Files | How It Works |
|------|--------------|--------------|
| **Cursor** | `.cursor/rules/*.mdc` | 4 rules: general, backend, frontend, security — all reference `AGENTS.md` |
| **Claude Code** | `CLAUDE.md` | Entry point — directs to `AGENTS.md` |
| **Codex** | `AGENTS.md` | Reads root `AGENTS.md` directly |
| **OpenCode** | `AGENTS.md` + `.agents/skills/*.yaml` | 3 skills: payagent-contracts, payagent-security-review, payagent-testing |

**Verification:** Open repository in each tool and confirm it loads the rules/skills without errors.

---

## PayPal Sandbox-Only Notice

⚠️ **This project uses PayPal Sandbox exclusively.**
- `PayPalEnvironmentGuard` fails fast if `payagent.paypal.base-url` ≠ `https://api-m.sandbox.paypal.com`
- No production endpoints, no real money, no live credentials in repo
- Each developer configures their own Sandbox Business + Personal accounts locally
- Webhook URL for local dev: use ngrok/cloud tunnel → configure in PayPal Developer Dashboard

---

## Mock Mode vs Real Integration

| Mode | Flag | Backend Required | Use Case |
|------|------|------------------|----------|
| **Mock (default)** | `NEXT_PUBLIC_USE_MOCKS=true` | No | Frontend UI development, contract testing, CI |
| **Real** | `NEXT_PUBLIC_USE_MOCKS=false` | Yes (API + Postgres + PayPal Sandbox) | End-to-end integration, demo, release gates |

**Switching:** Edit `.env` or Docker Compose environment, restart Web container.

---

## Security Notes

- **Mandate privacy:** Counterparty never sees your `MandateDTO`
- **Object-level auth:** Every access checked against deal membership + role
- **Secrets:** All from env vars (JWT, PayPal, evidence HMAC) — never hard-coded
- **PayPal webhooks:** Signature verified via `/v1/notifications/verify-webhook-signature`, deduplicated by `event_id`
- **Idempotency:** All mutating endpoints require `Idempotency-Key` header
- **Money:** Integer minor units (cents) only — `long`/`number`, never `float`/`double`
- **LLM boundary:** LLM never receives PayPal creds, never computes authoritative utility, never mutates DB
- **Dispute freeze:** Creating dispute → `DealStatus=DISPUTED`, blocks automated capture
- **Evidence:** SHA-256 on upload, stored outside web root, 15-min HMAC-signed download URLs

---

## Known Limitations (Scaffolding Status)

| Component | Status | Notes |
|-----------|--------|-------|
| Backend domain logic (auth, deals, mandates, agreements) | **Scaffolding only** | Contracts, config, health endpoint exist; controllers/services not implemented |
| PayPal integration (OAuth, Invoicing, Orders, Webhooks) | **Not implemented** | Member B scope |
| AI engine (intake extraction, counteroffers, sanitizer) | **Not implemented** | Member C scope; Spring AI dependency not in pom.xml |
| Evidence storage, disputes, WebSocket realtime | **Not implemented** | Member E scope |
| Frontend UI (Deal Room, Mandate Builder, Settlement Panel, Evidence Panel, Audit Timeline) | **Landing page only** | Member D scope; mock mode works with fixtures |
| Release gate tests (TST-01..TST-15) | **Placeholder only** | Member F scope |
| CI pipeline (full) | **Configured, not verified** | GitHub Actions YAML exists; Maven/Docker/Gitleaks blocked locally |

---

## Hackathon Milestones & Deadlines

| Milestone | Date (Africa/Cairo) | Target |
|-----------|---------------------|--------|
| **M1: Contract Lock & PayPal Smoke Proof** | Oct 24, 2026, 21:00 | Contracts merged, docker-compose up, PayPal OAuth+Invoice+Order curl proof |
| **M2: Core Domain, AI Extraction & Mock UI** | Oct 28, 2026, 21:00 | Negotiation + hash lock via REST, AI extraction <5s, UI on fixtures |
| **M3: PayPal Settlement, Bounded Agent & Evidence** | Nov 02, 2026, 21:00 | Full backend loop: Agreement → Invoice → Auth → Evidence → Capture |
| **M4: Full-Stack Integration & Feature Freeze** | Nov 05, 2026, 21:00 | Two-browser demo on staging with real Sandbox IDs; **no new features after** |
| **M5: Security Verification & Release Gates** | Nov 08, 2026, 21:00 | TST-01..TST-15 + RG-1..RG-5 pass, zero critical defects |
| **M6: Demo Recording & Early Devpost Submission** | **Nov 11, 2026, 22:00** | **HARD INTERNAL FREEZE** — Public repo, Apache-2.0, live demo URL, YouTube video (<2:50) submitted |
| **Official Deadline** | **Nov 12, 2026, 22:00** | Final Devpost submission |

> **Timezone note:** Egypt ends DST Oct 29, 2026; US ends DST Nov 1, 2026. All deadlines above are Africa/Cairo (EET, UTC+2).

---

## Documentation Index

- [`AGENTS.md`](AGENTS.md) — Authoritative agent instructions (architecture, ownership, contracts, security, commands)
- [`CLAUDE.md`](CLAUDE.md) — Claude Code entry point
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — Component boundaries, REST/WebSocket, data flow, module ownership
- [`docs/SECURITY_THREAT_MODEL.md`](docs/SECURITY_THREAT_MODEL.md) — Assets, trust boundaries, threats, mitigations, residual risks
- [`docs/LOCAL_DEVELOPMENT.md`](docs/LOCAL_DEVELOPMENT.md) — Environment setup, commands, ports, troubleshooting
- [`docs/AI_AGENT_SETUP.md`](docs/AI_AGENT_SETUP.md) — Cursor, Claude Code, Codex, OpenCode setup per tool
- [`docs/TEAM_HANDOFF.md`](docs/TEAM_HANDOFF.md) — Ownership boundaries, first tasks, contract change protocol, PR expectations
- [`docs/BOOTSTRAP_STATUS.md`](docs/BOOTSTRAP_STATUS.md) — Completed tasks, command outcomes, known issues, handoff checklist

---

## License

Apache License 2.0 — see [`LICENSE`](LICENSE).

---

## Repository

- **GitHub:** https://github.com/mona585/Payagent.git
- **Branch:** `main`
- **Owner:** mona585