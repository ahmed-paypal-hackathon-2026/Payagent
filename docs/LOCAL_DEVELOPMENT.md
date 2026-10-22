# Payagent — Local Development Guide

**Source of truth:** `AGENTS.md` §5, `.env.example`, `docker-compose.yml`, `plan v2.docx`.  
**Status:** Documents setup for the *current* repository state. Some tooling is blocked in this environment.

---

## 1. Prerequisites (Verify Locally)

| Tool | Required Version | Verify Command | Status in This Environment |
|------|------------------|----------------|----------------------------|
| Git | 2.40+ | `git --version` | ✅ Available |
| Java | 21 (Temurin/Eclipse) | `java -version` | ❓ Not verified |
| Maven | 3.9+ (or wrapper) | `mvn -version` | ❌ Wrapper download fails (network) |
| Node.js | 20.x | `node --version` | ✅ v24.18.1 |
| npm | 10.x | `npm --version` | ✅ 11.16.0 |
| Docker | 24+ | `docker --version` | ❌ Not installed |
| Docker Compose | v2 | `docker compose version` | ❌ Not installed |
| Gitleaks | 8.18+ | `gitleaks version` | ❌ Not installed |
| Bash/WSL | — | `bash --version` | ❌ Not available (Windows PowerShell) |

> **Note:** This guide assumes a standard development environment. The current sandbox has network/tooling restrictions. Adapt commands for your OS (Windows PowerShell vs. Bash).

---

## 2. One-Time Setup

```bash
# 1. Clone repository
git clone https://github.com/mona585/Payagent.git
cd Payagent

# 2. Configure environment (never commit .env)
# Windows PowerShell:
Copy-Item .env.example .env
# Linux/macOS:
# cp .env.example .env

# 3. Edit .env with your local values (minimum required):
#    PAYAGENT_JWT_SECRET=your-32-char-random-string-here
#    PAYPAL_CLIENT_ID=your-sandbox-client-id          # Member B only
#    PAYPAL_CLIENT_SECRET=your-sandbox-client-secret  # Member B only
#    PAYPAL_WEBHOOK_ID=your-sandbox-webhook-id        # Member B only
#    SPRING_AI_OPENAI_API_KEY=your-llm-key            # Member C only
#    SPRING_AI_OPENAI_BASE_URL=https://your-llm-endpoint # Member C only
```

**Generate a JWT secret:**
```bash
# PowerShell:
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
# Linux/macOS:
# openssl rand -base64 32
```

---

## 3. Docker Compose (Recommended — Full Stack)

### 3.1 Start All Services
```bash
# From repo root
docker compose up --build -d
```

**Expected output:**
- `payagent-postgres` — PostgreSQL 16.4 on port 5432
- `payagent-api` — Spring Boot on port 8080
- `payagent-web` — Next.js on port 3000

### 3.2 Verify Health
```bash
# API health
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP",...}

curl http://localhost:8080/api/v1/health
# Expected: {"status":"UP","service":"payagent-api"}

# Web health
curl http://localhost:3000
# Expected: HTML landing page
```

### 3.3 View Logs
```bash
docker compose logs -f api     # Spring Boot logs
docker compose logs -f web     # Next.js logs
docker compose logs -f postgres # PostgreSQL logs
```

### 3.4 Stop & Cleanup
```bash
docker compose down            # Stop, keep volumes
docker compose down -v         # Stop + remove volumes (fresh DB next start)
```

---

## 4. Local Development Without Docker

### 4.1 Backend (Spring Boot)
**Prerequisites:** Local PostgreSQL 16 running on `localhost:5432` with database `payagent`, user `payagent`, password from `.env`.

```bash
cd services/api

# Run tests (H2 in-memory, no Postgres needed)
./mvnw -B test

# Run application (requires local Postgres)
./mvnw -B spring-boot:run

# Package JAR (skip tests)
./mvnw -B -DskipTests package
```

**Application profiles:**
- `local` (default) — connects to `localhost:5432`
- `test` — H2 in-memory, excludes Flyway/DataSource auto-config
- `docker` — connects to `postgres:5432` (service name)

### 4.2 Frontend (Next.js)
```bash
cd apps/web

# Install dependencies (uses package-lock.json)
npm ci

# Development server (mock mode by default)
npm run dev
# → http://localhost:3000

# Type checking
npm run typecheck

# Linting
npm run lint

# Production build
npm run build

# Run production build
npm run start
```

**Mock mode:** Controlled by `NEXT_PUBLIC_USE_MOCKS`:
- `true` (default) — serves `shared/fixtures/` locally, no backend needed
- `false` — calls real API at `NEXT_PUBLIC_API_BASE_URL`

---

## 5. Environment Variables Reference

| Variable | Default (Docker) | Default (Local) | Required For |
|----------|------------------|-----------------|--------------|
| `POSTGRES_DB` | `payagent` | — | Docker Compose |
| `POSTGRES_USER` | `payagent` | — | Docker Compose |
| `POSTGRES_PASSWORD` | `change-me-local` | — | Docker Compose |
| `POSTGRES_PORT` | `5432` | — | Docker Compose |
| `PAYAGENT_DB_URL` | `jdbc:postgresql://postgres:5432/payagent` | `jdbc:postgresql://localhost:5432/payagent` | Backend |
| `PAYAGENT_DB_USER` | `payagent` | `payagent` | Backend |
| `PAYAGENT_DB_PASSWORD` | `change-me-local` | `change-me-local` | Backend |
| `PAYAGENT_JWT_SECRET` | `local-dev-only-change-before-shared-demo` | *(must set)* | Backend |
| `PAYAGENT_API_PORT` | `8080` | `8080` | Backend |
| `PAYPAL_API_BASE_URL` | `https://api-m.sandbox.paypal.com` | `https://api-m.sandbox.paypal.com` | Member B |
| `PAYPAL_CLIENT_ID` | *(empty)* | *(empty)* | Member B |
| `PAYPAL_CLIENT_SECRET` | *(empty)* | *(empty)* | Member B |
| `PAYPAL_WEBHOOK_ID` | *(empty)* | *(empty)* | Member B |
| `SPRING_AI_OPENAI_API_KEY` | *(empty)* | *(empty)* | Member C |
| `SPRING_AI_OPENAI_BASE_URL` | *(empty)* | *(empty)* | Member C |
| `SPRING_AI_OPENAI_MODEL` | `gpt-4o-mini` | `gpt-4o-mini` | Member C |
| `PAYAGENT_EVIDENCE_STORAGE_PATH` | `/var/payagent/evidence` | `./data/evidence` | Member E |
| `PAYAGENT_EVIDENCE_SIGNING_SECRET` | `local-evidence-signing-secret` | *(must set)* | Member E |
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | `http://localhost:8080` | Frontend |
| `NEXT_PUBLIC_USE_MOCKS` | `true` | `true` | Frontend |
| `WEB_PORT` | `3000` | `3000` | Frontend |

---

## 6. Common Commands Cheat Sheet

### Backend (`services/api/`)
```bash
./mvnw -B test                              # All tests
./mvnw -B test -Dtest=HealthControllerTest  # Single test class
./mvnw -B test -Dtest=ContractTypeScriptParityTest
./mvnw -B test -Dtest=ContractFixtureDeserializationTest
./mvnw -B -DskipTests package               # Build JAR
./mvnw -B spring-boot:run                   # Run (local profile)
./mvnw -B spring-boot:run -Dspring.profiles.active=docker  # Run (docker profile)
```

### Frontend (`apps/web/`)
```bash
npm ci                                      # Clean install
npm run dev                                 # Dev server (mock mode)
npm run typecheck                           # tsc --noEmit
npm run lint                                # next lint
npm run build                               # Production build
npm run start                               # Run production server
```

### Fixtures (Repo Root)
```bash
node scripts/validate-fixtures.mjs          # Validate all 10 JSON fixtures
```

### Security (Repo Root)
```bash
gitleaks detect --source . --verbose        # Secret scan
bash scripts/check-ai-slop-comments.sh      # Placeholder comment check
# PowerShell fallback:
git grep -E "(?i)(as an ai|todo: implement|placeholder implementation|fake success|lorem ipsum)" -- "services/api/src/main" "apps/web/src"
```

### Docker (Repo Root)
```bash
docker compose config                       # Validate config
docker compose up --build -d                # Start all
docker compose ps                           # Status
docker compose logs -f api                  # Follow API logs
docker compose down                         # Stop
docker compose down -v                      # Stop + wipe volumes
```

---

## 7. Windows-Specific Notes

### 7.1 Path Separators
- Use forward slashes in commands: `docker compose -f docker-compose.yml config`
- PowerShell: `Copy-Item .env.example .env` (not `cp`)

### 7.2 Maven Wrapper
```powershell
# In services/api/
.\mvnw.cmd -B test
```

### 7.3 Line Endings
- `.gitignore` handles CRLF/LF
- No action needed if `core.autocrlf=input` (recommended)

### 7.4 Docker Desktop
- Enable "Use WSL 2 based engine" for best performance
- Allocate ≥4GB RAM, ≥2 CPUs

### 7.5 Port Conflicts
- Default ports: 5432 (Postgres), 8080 (API), 3000 (Web)
- Change in `.env` if conflicts: `POSTGRES_PORT=5433`, `PAYAGENT_API_PORT=8081`, `WEB_PORT=3001`

---

## 8. Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| `./mvnw: Permission denied` | Wrapper not executable | `chmod +x mvnw` (Linux/macOS) or use `mvnw.cmd` (Windows) |
| `Could not find or load main class org.apache.maven.wrapper.MavenWrapperMain` | Wrapper JAR missing/corrupt | Delete `.mvn/wrapper/maven-wrapper.jar`, re-run `mvnw` |
| `Connection refused: localhost:5432` | Postgres not running | Start Postgres or use `docker compose up -d postgres` |
| `Flyway validate failed` | Schema mismatch | `docker compose down -v` (wipes DB), then `docker compose up --build` |
| `npm ERR! code ENOENT` | Wrong directory | `cd apps/web` before `npm` commands |
| `NEXT_PUBLIC_USE_MOCKS=true` but UI shows errors | Fixtures not loading | Check `shared/fixtures/` exists; run `node scripts/validate-fixtures.mjs` |
| `gitleaks: command not found` | Not installed | `brew install gitleaks` / `choco install gitleaks` / download binary |
| `bash: command not found` | No Bash on Windows | Use PowerShell `git grep` fallback (see §6) |
| PayPal `401 Unauthorized` | Invalid sandbox credentials | Verify `PAYPAL_CLIENT_ID`/`SECRET` in `.env` match Developer Dashboard |
| WebSocket connection fails | JWT missing/expired | Check browser devtools Network → WS → Headers; ensure JWT in `Authorization` header |

---

## 9. Debugging Tips

### Backend
- Enable debug logging: `logging.level.com.payagent=DEBUG` in `application-local.yml`
- Actuator endpoints: `/actuator/health`, `/actuator/info`, `/actuator/env` (if exposed)
- Database: Connect with `psql -h localhost -U payagent -d payagent`

### Frontend
- React DevTools + Redux DevTools (if added)
- Network tab: filter `/api/v1/` for REST, `ws://` for WebSocket
- Console: `NEXT_PUBLIC_USE_MOCKS` value logged on load

### PayPal Sandbox
- Dashboard: https://developer.paypal.com/dashboard/
- Sandbox accounts: Create Business (seller) + Personal (buyer)
- Webhook simulator: Use ngrok `https://xxx.ngrok-free.app/api/v1/paypal/webhooks`

---

## 10. Current Environment Blockers (This Sandbox)

| Blocker | Impact | Workaround |
|---------|--------|------------|
| Maven wrapper download fails (network) | Backend tests/build **BLOCKED** | Use local Maven install; configure proxy; or run in environment with internet |
| Docker not installed | Full stack startup **BLOCKED** | Install Docker Desktop; or run backend/frontend locally with local Postgres |
| Gitleaks not installed | Secret scan **BLOCKED** | Install Gitleaks binary; or run in CI only |
| Bash/WSL not available | AI-slop script **BLOCKED** | Use PowerShell `git grep` fallback (works on tracked files) |
| No PayPal sandbox creds configured | PayPal integration **NOT TESTABLE** | Member B must add creds to local `.env` |

---

## 11. Verification Checklist (Before Committing)

- [ ] `git status --short` — only intended changes
- [ ] `git diff --check` — no whitespace errors
- [ ] Backend: `./mvnw -B test` — **PASS** (when unblocked)
- [ ] Frontend: `npm ci && npm run typecheck && npm run lint && npm run build` — **PASS**
- [ ] Fixtures: `node scripts/validate-fixtures.mjs` — **PASS** (10/10)
- [ ] Docker: `docker compose config` — **PASS** (when unblocked)
- [ ] Secrets: `gitleaks detect --source .` — **PASS** (zero findings)
- [ ] Placeholders: `bash scripts/check-ai-slop-comments.sh` — **PASS** (clean)

---

## 12. Useful Aliases (Add to Shell Profile)

```bash
# ~/.bashrc or ~/.zshrc or PowerShell $PROFILE
alias payagent-backend='cd services/api && ./mvnw'
alias payagent-frontend='cd apps/web && npm'
alias payagent-fixtures='node scripts/validate-fixtures.mjs'
alias payagent-secrets='gitleaks detect --source . --verbose'
alias payagent-docker='docker compose -f docker-compose.yml'
```