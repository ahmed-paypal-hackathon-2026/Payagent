# AI Benchmark — Structured Intake Extraction (WP-C1 / Gate 2 Criterion 2)

**Owner:** Member C
**Acceptance target (plan §5, Gate 2):** 100% JSON schema validity across 10 sample freelance prompts in under 5s p95 latency.

---

## 1. Implementation Status

| Component | Status |
|---|---|
| `com.payagent.ai.IntakeExtractionService` | Implemented — strict JSON-schema prompt, markdown-fence stripping, per-field validation |
| `com.payagent.ai.ChatClient` interface | Implemented — pluggable LLM client abstraction |
| Deterministic `FixtureChatClient` (test scope) | Implemented — fixture-backed, no network calls |
| Spring AI-backed `ChatClient` (production) | **NOT IMPLEMENTED** — requires `spring-ai-openai-spring-boot-starter` dependency + `SPRING_AI_OPENAI_API_KEY` / `SPRING_AI_OPENAI_BASE_URL` credentials |
| `docs/AI_BENCHMARK.md` | This file |

The production `ChatClient` implementation is the single remaining external
prerequisite. It must be a Spring AI `ChatClient` adapter configured against the
OpenAI-compatible endpoint specified by `SPRING_AI_OPENAI_BASE_URL`, using the
system instruction defined in
`IntakeExtractionService.SYSTEM_INSTRUCTION`.

---

## 2. Local Fixture-Path Measurements (Observed)

Measured on the deterministic `FixtureChatClient` (no network calls).
These exercise the full extraction path: prompt → response → JSON parse →
schema validation → `StructuredTermsDTO` construction.

| Metric | Value | Evidence |
|---|---|---|
| Single extraction latency (landing-page fixture) | < 0.05s | `IntakeExtractionServiceTest.extractDealIntake_latencyUnder5Seconds_localFixturePath` — asserts < 5.0s, observed far below |
| 10-prompt sweep worst-case latency | < 0.05s | `IntakeExtractionServiceTest.extractDealIntake_tenPrompts_allValidAndUnder5SecondsEach` — asserts worst case < 5.0s |
| Schema validity across 10 prompts | 100% (14/14 tests, 0 failures) | `mvnw -B test -Dtest=IntakeExtractionServiceTest` → Tests run: 14, Failures: 0 |

Test command and observed output:
```
cd services/api && ./mvnw.cmd -B test -Dtest=IntakeExtractionServiceTest
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**Important:** these are local fixture-path measurements, NOT live-provider
latency. The fixture path removes network latency entirely, so it cannot be
used to claim the under-5-second criterion against a real model.

---

## 3. Live-Provider Verification (PENDING)

The under-5-second p95 criterion against a real model requires:

1. **Spring AI dependency** — add `spring-ai-openai-spring-boot-starter` to
   `services/api/pom.xml` (version 1.0.x per AGENTS.md §1).
2. **Credentials** — set `SPRING_AI_OPENAI_API_KEY` and
   `SPRING_AI_OPENAI_BASE_URL` (managed endpoint, or self-hosted vLLM/Ollama).
3. **Production `ChatClient`** — implement a Spring AI-backed
   `ChatClient` bean that calls `ChatModel.call(...)` with
   `IntakeExtractionService.SYSTEM_INSTRUCTION`.
4. **20-prompt benchmark** — run the 20-prompt corpus recording per-prompt
   latency and JSON schema validity; compute p95 and record it in the table
   below.

| Metric (live provider) | Value |
|---|---|
| p95 latency (20 prompts) | **NOT MEASURED** |
| Schema validity (20 prompts) | **NOT MEASURED** |
| Model / endpoint | **NOT CONFIGURED** |

**Blocker:** no LLM credentials or endpoint are available in this environment.
The local fixture path is complete and tested; live verification is the only
remaining step for Gate 2 Criterion 2.

---

## 4. Failure Paths Covered

The extractor rejects (with `IntakeExtractionException`) and tests assert:

- Blank or null raw prompt
- Model returning null / empty output
- Model output that is not valid JSON
- Missing `title`, `deliverables`, or any required field
- Empty `deliverables` array
- Negative `priceMinorUnits` or `includedRevisions`
- Zero/negative `deliveryDays`
- Markdown-fenced JSON is **accepted** (fences are stripped before parsing)
