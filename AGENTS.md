# PAYAGENT — AGENTS.md

Authoritative repository-wide rules for all AI agents. Source of truth: `plan v2.docx` extracted to `docs/_plan_extract.txt`. Last synced: 2026-10-25.

## Stack & Layout

- Frontend: Next.js 14 App Router + React 18 + TypeScript 5 + Tailwind 3.4 — `apps/web/`
- Backend: Spring Boot 3.3.5 + Java 21 + Maven wrapper — `services/api/`
- DB: PostgreSQL 16 via Docker Compose
- AI: Spring AI 1.0.x OpenAI-compatible — Member C only
- PayPal: Sandbox only, Invoicing v2 / Orders v2 / Payments v2 / Webhooks — Member B only
- Build: `./mvnw` in `services/api/`, `npm` in `apps/web/`
- Monorepo: `apps/web/`, `services/api/src/main/java/com/payagent/`, `shared/fixtures/`, `docs/`

## Ownership Boundaries — Do Not Cross

| Member | Owns | Must NOT Touch |
|--------|------|----------------|
| A | `services/api/.../common/contracts/**`, `auth/**`, `deals/**`, `mandates/**`, `agreements/**`, `db/migration/**` | `paypal/**`, `ai/**`, `apps/web/**` |
| B | `services/api/.../paypal/**`, `settlement/**` | `ai/**`, `apps/web/**`, `mandates/**` utility math |
| C | `services/api/.../ai/**`, `docs/AI_BENCHMARK.md` | `paypal/**`, `apps/web/**`, DB mutations, mandate decisions |
| D | `apps/web/**` | Backend domain logic, PayPal credentials, authoritative financial status |
| E | `LICENSE`, `.gitignore`, `.env.example`, `docker-compose.yml`, `render.yaml`, `.github/workflows/ci.yml`, `evidence/**`, `disputes/**`, `realtime/**` | `paypal/**`, `ai/**` |
| F | `shared/fixtures/**`, `services/api/src/test/java/.../releasegates/**`, `README.md`, `docs/SECURITY_THREAT_MODEL.md` | Other members' implementation paths |

Contract changes: Java DTOs/Enums ↔ TypeScript mirror require synchronized PRs from A + D.

## Canonical Contracts

Java: `com.payagent.common.contracts` — 7 enums + 7 records
TypeScript mirror: `apps/web/src/types/contracts.ts`

Parity test: `services/api/src/test/java/com/payagent/bootstrap/ContractTypeScriptParityTest.java` — run before merging contract changes.

Key types: `StructuredTermsDTO`, `MandateDTO`, `OfferDTO`, `AgreementSnapshotDTO`, `SettlementStateDTO`, `DeliverableEvidenceDTO`, `DealWorkspaceDTO`. All money fields are `long` minor units (cents).

## Security Invariants

- Mandate privacy: `MandateDTO` never serialized to counterparty. `DealWorkspaceDTO.myMandate` scoped to authenticated user only.
- Object-level auth on every deal/mandate/evidence/settlement access → 403 on cross-account.
- No hard-coded secrets. All from env vars. `.env` never committed.
- PayPal Sandbox only: `PayPalEnvironmentGuard` fails fast if base URL ≠ `https://api-m.sandbox.paypal.com`.
- Webhook verification via `POST /v1/notifications/verify-webhook-signature`, deduplicate by `paypal_events.event_id` PK.
- Idempotency-Key header required on all mutating endpoints.
- Money = integer minor units. Never float/double.
- LLM trust boundary: LLM never receives PayPal credentials, never computes authoritative utility scores, never mutates DB. Outputs validated by `MandateService` + `UtilityCalculator`.
- Prompt injection defense: counterparty text wrapped in `<untrusted_counterparty_data>...</untrusted_counterparty_data>`. Output schema validated.
- Agreement hash integrity: `termsHashSha256` = SHA-256 of RFC-8785 canonical JSON of `canonicalTerms`. Change → new version, new hash, prior acceptances cleared.
- Dispute freeze: `POST /api/v1/deals/{id}/disputes` → `DealStatus=DISPUTED`, blocks automated capture.
- Evidence privacy: stored outside web root, SHA-256 on upload, 15-min HMAC-signed download URLs checked against deal membership.
- UNKNOWN-state reconciliation: PayPal `UNKNOWN` states trigger reconciliation, no silent success.

## Commands

Backend `services/api/`:
```bash
./mvnw -B test               # H2 profile, no DB needed
./mvnw -B -DskipTests package
```

Frontend `apps/web/`:
```bash
npm ci
npm run typecheck            # tsc --noEmit
npm run lint
npm run dev                  # mock mode by default, NEXT_PUBLIC_USE_MOCKS=true
npm run build
```

Repo root:
```bash
node scripts/validate-fixtures.mjs
docker compose config
docker compose up --build -d    # API http://localhost:8080, Web http://localhost:3000
docker compose down -v
gitleaks detect --source . --verbose
bash scripts/check-ai-slop-comments.sh
```

## Agent Rules

- Read file and `git status --short` before editing.
- Preserve existing work; never overwrite teammate's implementation.
- No fake success for security/financial operations.
- Report verification as PASS/FAIL/BLOCKED/NOT RUN.
- Never print or commit secrets.
- Sandbox only: no production PayPal, no real money.
- Contract changes → update Java and TypeScript together, run parity test.
- New backend logic → unit test. New frontend → typecheck + lint pass.

## Environment

Copy `.env.example` → `.env`. Required: `PAYAGENT_JWT_SECRET` ≥32 chars, `PAYPAL_API_BASE_URL=https://api-m.sandbox.paypal.com`, `PAYAGENT_DB_URL`/`USER`/`PASSWORD`. Frontend uses `NEXT_PUBLIC_API_BASE_URL` and `NEXT_PUBLIC_USE_MOCKS`. All secrets from env, never hard-coded.
