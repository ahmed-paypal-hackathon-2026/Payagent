# Payagent — AI Agent Setup Guide

**Source of truth:** `AGENTS.md`, `.cursor/rules/*.mdc`, `CLAUDE.md`, `.agents/skills/*.yaml`.  
**Status:** Documents setup for the *current* repository configuration.

---

## 1. Overview

This repository supports four AI coding assistants. All tools reference the single source of truth: **`AGENTS.md`** at the repository root.

| Tool | Primary Config | Secondary Config | How It Works |
|------|----------------|------------------|--------------|
| **Cursor** | `.cursor/rules/*.mdc` (4 files) | `AGENTS.md` | Project rules auto-loaded by Cursor |
| **Claude Code** | `CLAUDE.md` | `AGENTS.md` | Entry point directs to `AGENTS.md` |
| **Codex** | `AGENTS.md` | — | Reads root `AGENTS.md` directly |
| **OpenCode** | `AGENTS.md` + `.agents/skills/*.yaml` | — | Reads `AGENTS.md`; skills loaded via CLI |

---

## 2. Cursor Setup

### 2.1 Required Files (Already in Repo)
```
.cursor/
└── rules/
    ├── general.mdc      # Repository-wide rules (all files)
    ├── backend.mdc      # Spring Boot / Java scoped (services/api/**)
    ├── frontend.mdc     # Next.js / TypeScript scoped (apps/web/**)
    └── security.mdc     # PayPal / Security scoped (paypal/**, settlement/**, evidence/**, etc.)
```

### 2.2 Cursor Rule Format
- **Frontmatter:** `description`, `globs`, `alwaysApply`
- **Body:** Markdown with rules, references to `AGENTS.md`
- **Version:** Compatible with Cursor 0.43+ (project rules)

### 2.3 Verification
1. Open repository in Cursor
2. Press `Cmd/Ctrl + Shift + P` → "Cursor: Open Rules"
3. Confirm 4 rules listed: `general`, `backend`, `frontend`, `security`
4. Open a Java file in `services/api/` → `backend.mdc` should apply
5. Open a TypeScript file in `apps/web/` → `frontend.mdc` should apply
6. Open a PayPal-related Java file → `security.mdc` should apply

### 2.4 Known Limitations
- Cursor does not auto-reload rules on file change; restart Cursor after rule edits
- Rule precedence: more specific `globs` win over `alwaysApply: true`

---

## 3. Claude Code Setup

### 3.1 Required Files (Already in Repo)
```
CLAUDE.md    # Root entry point
AGENTS.md    # Actual rules (referenced by CLAUDE.md)
```

### 3.2 CLAUDE.md Content
```markdown
# CLAUDE.md — Payagent Repository Instructions for Claude Code

## Primary Directive
**Read and follow `AGENTS.md` for all repository-wide rules...**
```

### 3.3 Verification
1. Run `claude` in repository root
2. Ask: "What are the ownership boundaries for this project?"
3. Claude should reference `AGENTS.md` §2 (six-person ownership table)
4. Ask: "How do I run backend tests?"
4. Claude should reference `AGENTS.md` §5 (verified commands)

### 3.4 Known Limitations
- Claude Code does not auto-discover `.cursor/rules/` or `.agents/skills/`
- It only reads `CLAUDE.md` and `AGENTS.md` from root
- No skill/plugin system — relies on context window

---

## 4. Codex Setup

### 4.1 Required Files (Already in Repo)
```
AGENTS.md    # Primary and only required file
```

### 4.2 How Codex Uses AGENTS.md
- Codex reads `AGENTS.md` at session start
- It treats the file as system instructions
- No additional config files needed

### 4.3 Verification
1. Open repository in Codex (VS Code extension or web)
2. Ask: "What is the canonical contract package in this project?"
3. Codex should answer: `com.payagent.common.contracts` (Java) and `apps/web/src/types/contracts.ts` (TypeScript)
4. Ask: "What is the mandate privacy rule?"
4. Codex should reference `AGENTS.md` §4 invariant #1

### 4.4 Known Limitations
- Codex has no project-specific config beyond `AGENTS.md`
- Cannot load skills or scoped rules
- Large `AGENTS.md` may consume significant context tokens

---

## 5. OpenCode Setup

### 5.1 Required Files (Already in Repo)
```
AGICS.md                           # Primary rules
.agents/
└── skills/
    ├── payagent-contracts/
    │   └── skill.yaml            # Contract parity, fixture validation
    ├── payagent-security-review/
    │   └── skill.yaml            # Secret scan, mandate privacy, idempotency, etc.
    └── payagent-testing/
        └── skill.yaml            # Backend/frontend/fixture tests, CI simulation
```

### 5.2 OpenCode Skill Format
```yaml
name: payagent-contracts
version: "1.0.0"
description: "Canonical contract management..."
commands:
  - name: check-parity
    description: "Run Java/TypeScript contract parity test"
    run: |
      cd services/api && ./mvnw -B test -Dtest=ContractTypeScriptParityTest
```

### 5.3 Verification (OpenCode v2.1.3)
```bash
# List available skills
opencode plugin list

# Run a skill command
opencode run "payagent-contracts check-parity"

# Or in interactive mode:
# > payagent-contracts check-parity
```

### 5.4 Skill Discovery
- OpenCode loads skills from `.agents/skills/*/skill.yaml`
- Skills are available as slash commands in chat: `/payagent-contracts check-parity`
- Skills can also be invoked programmatically via `opencode run`

### 5.5 Known Limitations
- OpenCode skill format is undocumented; based on CLI `opencode plugin add` behavior
- No official schema validation for `skill.yaml`
- Skills run in shell; require tooling (Maven, Node, Docker) to be in PATH
- No auto-reload — restart OpenCode after skill changes

---

## 6. Cross-Tool Consistency Checklist

| Rule Area | Cursor | Claude Code | Codex | OpenCode | Verified |
|-----------|--------|-------------|-------|----------|----------|
| Architecture & stack | ✅ `.cursor/rules/general.mdc` | ✅ `AGENTS.md` | ✅ `AGENTS.md` | ✅ `AGENTS.md` | ✅ |
| Six-person ownership | ✅ `general.mdc` | ✅ `AGENTS.md` | ✅ `AGENTS.md` | ✅ `AGENTS.md` | ✅ |
| Canonical contracts | ✅ `general.mdc` + `backend.mdc` | ✅ `AGENTS.md` | ✅ `AGENTS.md` | ✅ `AGENTS.md` + skill | ✅ |
| Security invariants | ✅ `security.mdc` | ✅ `AGENTS.md` | ✅ `AGENTS.md` | ✅ `AGENTS.md` + skill | ✅ |
| Dev commands | ✅ `general.mdc` | ✅ `AGENTS.md` | ✅ `AGENTS.md` | ✅ `AGENTS.md` + skill | ✅ |
| Contract parity test | ✅ `backend.mdc` | ✅ `AGENTS.md` | ✅ `AGENTS.md` | ✅ skill command | ✅ |
| Fixture validation | ✅ `general.mdc` | ✅ `AGENTS.md` | ✅ `AGENTS.md` | ✅ skill command | ✅ |
| Secret scanning | ✅ `security.mdc` | ✅ `AGENTS.md` | ✅ `AGENTS.md` | ✅ skill command | ✅ |

---

## 7. Tool-Specific Commands Quick Reference

### Cursor (via Command Palette or Chat)
- No special commands — rules apply automatically based on file globs
- Ask in chat: "Check this file against Payagent security rules"

### Claude Code (Chat)
```
> What are the PayPal sandbox-only requirements?
> Run the contract parity test for me
> Show me the mandate privacy rule
```

### Codex (Chat)
```
> Generate a new OfferDTO in TypeScript matching the Java canonical
> Write a test for the UtilityCalculator BigDecimal formulas
```

### OpenCode (CLI or Chat)
```bash
# CLI
opencode run "payagent-contracts check-parity"
opencode run "payagent-security-review scan-secrets"
opencode run "payagent-testing frontend-test"

# Interactive chat
> /payagent-contracts validate-fixtures
> /payagent-security-review review-mandate-privacy
> /payagent-testing full-ci-simulation
```

---

## 8. Adding/Updating Agent Configuration

### 8.1 When to Update
- New security invariant → update `AGENTS.md` §4 + `.cursor/rules/security.mdc`
- New ownership boundary → update `AGENTS.md` §2 + all tool configs
- New dev command → update `AGENTS.md` §5 + relevant scoped rules
- New skill needed → add `.agents/skills/new-skill/skill.yaml`

### 8.2 Update Process
1. Edit `AGENTS.md` (single source of truth)
2. Propagate to tool-specific files:
   - Cursor: Update relevant `.cursor/rules/*.mdc`
   - OpenCode: Update/create `.agents/skills/*/skill.yaml`
3. Verify each tool:
   - Cursor: Restart, check rules load
   - Claude Code: Ask ownership question
   - Codex: Ask contract question
   - OpenCode: Run skill command
4. Commit all changes together

### 8.3 Do Not
- Edit tool-specific files without updating `AGENTS.md`
- Add conflicting rules in scoped configs
- Invent commands that don't exist in `AGENTS.md` §5

---

## 9. Current Verification Status (This Environment)

| Tool | Config Present | Load Test | Functional Test | Status |
|------|----------------|-----------|-----------------|--------|
| Cursor | ✅ 4 `.mdc` files | ❌ No Cursor IDE | ❌ Cannot verify | **CONFIGURED** |
| Claude Code | ✅ `CLAUDE.md` | ❌ No Claude CLI | ❌ Cannot verify | **CONFIGURED** |
| Codex | ✅ `AGENTS.md` | ❌ No Codex access | ❌ Cannot verify | **CONFIGURED** |
| OpenCode | ✅ `AGENTS.md` + 3 skills | ✅ `opencode --version` works | ⚠️ Skills load but need Maven/Node/Docker | **PARTIAL** |

---

## 10. Troubleshooting

| Issue | Tool | Fix |
|-------|------|-----|
| Rules not applying | Cursor | Restart Cursor; check `.cursor/rules/` globs match file paths |
| `CLAUDE.md` not found | Claude Code | Must be in repository root; restart Claude session |
| Skill not found | OpenCode | Check `.agents/skills/*/skill.yaml` exists; `opencode plugin list` |
| Skill command fails | OpenCode | Ensure Maven/Node/Docker in PATH; check `run` script syntax |
| Context too large | Codex | `AGENTS.md` is ~400 lines; consider splitting if grows |
| Conflicting advice | Any | `AGENTS.md` wins — report discrepancy in tool config |

---

## 11. Future Enhancements (Not Implemented)

- [ ] Cursor: Add `.cursor/rules/testing.mdc` for release gate patterns
- [ ] Claude Code: Add `.claude/commands/*.md` for custom slash commands
- [ ] Codex: Add `.codex/config.yaml` if Codex adds project config support
- [ ] OpenCode: Add skill for PayPal smoke test (Member B)
- [ ] All: Automated sync script to propagate `AGENTS.md` changes to tool configs