# CLAUDE.md — Payagent Repository Instructions for Claude Code

## Primary Directive

**Read and follow `AGENTS.md` for all repository-wide rules, architecture, ownership boundaries, contracts, security invariants, and development commands.**

This file exists only to satisfy Claude Code's convention of looking for `CLAUDE.md` at the repository root. It adds **no additional rules** beyond what is in `AGENTS.md`.

## How to Use This Repository

1. **Start with `AGENTS.md`** — It contains the complete, authoritative specification for this project.
2. **Respect ownership boundaries** — Six team members own disjoint paths. Do not edit outside your assigned area without coordination.
3. **Follow the contract parity rule** — Java DTOs/Enums (`services/api/src/main/java/com/payagent/common/contracts/`) and TypeScript mirrors (`apps/web/src/types/contracts.ts`) must stay in sync. Run `ContractTypeScriptParityTest` before merging contract changes.
4. **Use the verified commands** — All build, test, and validation commands are documented in `AGENTS.md` §5.
5. **Never commit secrets** — `.env` is gitignored. Use `.env.example` as template.

## Quick Reference

| Task | Command | Location |
|------|---------|----------|
| Backend tests | `./mvnw -B test` | `services/api/` |
| Frontend typecheck | `npm run typecheck` | `apps/web/` |
| Frontend build | `npm run build` | `apps/web/` |
| Fixture validation | `node scripts/validate-fixtures.mjs` | Repo root |
| Docker config check | `docker compose config` | Repo root |
| Secret scan | `gitleaks detect --source .` | Repo root |

## Tool-Specific Notes

- **Cursor:** Rules in `.cursor/rules/` reference `AGENTS.md`.
- **Codex:** Reads `AGENTS.md` directly.
- **OpenCode:** Reads `AGENTS.md` and skills in `.agents/skills/`.

---

**If `AGENTS.md` and this file ever conflict, `AGENTS.md` wins.**