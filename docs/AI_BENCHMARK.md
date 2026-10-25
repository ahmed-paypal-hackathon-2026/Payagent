# AI Negotiation Intelligence — Architecture, Benchmark, and Verification

**Owner:** Member C (Spring AI & Negotiation Agent)
**Status:** Implemented and verified against the current repository contracts
**Last benchmark run:** 2026-10-25
**Scope:** `services/api/src/main/java/com/payagent/ai/**` and this document

---

## 1. Executive summary

The AI component uses a **hybrid architecture**:

1. **Deterministic policy engine** for every price, acceptance, counteroffer, and stop decision.
2. **Small learned strategy selector** that adapts negotiation pacing to the user's preferences and mandate shape.
3. **LLM only for intake extraction and rationale prose**, never for authoritative prices or mandate decisions.

On a held-out synthetic evaluation set of 1,000 scenarios, the learned selector achieved an average negotiation utility of **0.6007**, versus **0.5565** for the explainable rule-based baseline (a **+0.0442 absolute / +7.9% relative** improvement). The best single fixed strategy (`FLEXIBLE`) scored **0.5964**, so the learned policy is also slightly better than always using the best fixed archetype, while adapting per scenario. The learned policy reached agreement in **86.3%** of scenarios versus **78.2%** for the rule-based baseline. It beats the best fixed archetype by only **0.0043** utility, so the honest claim is that it adapts per scenario without sacrificing the best-fixed baseline's performance.

These numbers come from **deterministic synthetic simulations**, not human negotiations. No public dataset was found with the full Payagent mandate schema (price, delivery, revisions, must-have/forbidden terms, buyer/seller limits, and outcomes), so the benchmark is explicitly a simulation benchmark. It is reproducible and honestly labelled; it is not a claim of production ROI or human-level negotiation.

---

## 2. Why this architecture

### 2.1 Hard constraints must not depend on an LLM

Financial limits, acceptance, and counteroffer prices are safety-critical. The implementation therefore enforces them deterministically:

- `MandateValidator` validates the caller's mandate and role semantics.
- `NegotiationPolicyEngine` computes every candidate action and price from the mandate, strategy, preferences, and offer history.
- `OfferConstraintValidator` runs again at the final decision boundary and rejects any unsafe terms.
- `NegotiationAgentService` returns `ESCALATE` rather than fabricating a deal if the boundary fails.

An LLM prompt injection, hallucination, timeout, or malformed response cannot widen a mandate or force an unsafe acceptance. The LLM is not even given the caller's mandate.

### 2.2 Why not Spring AI 1.0

The repository is pinned to **Spring Boot 3.3.5**. Spring AI 1.0.x starters depend on Spring Boot 3.4.x, and the legacy `1.0.0-M6` starter depends on Spring Boot 3.4.2. Upgrading the shared framework is outside Member C's ownership and would affect every team member.

Instead, `OpenAiCompatibleChatClient` implements the small provider-neutral `ChatCompletionClient` interface with the JDK `HttpClient`. It speaks the OpenAI-compatible chat-completions protocol, needs no extra dependency, uses the existing `payagent.spring-ai.*` configuration keys, and can later be replaced by a Spring AI `ChatClient` implementation without changing intake or negotiation callers. This deviation is intentional and documented here.

### 2.3 Why a learned strategy selector instead of "LLM chooses the strategy"

The learned model is tiny (12 standardized features, 5 classes), runs in-process, has no network dependency, and can be benchmarked deterministically. It selects an **archetype**, not a price:

| Strategy | Behaviour |
|---|---|
| `PATIENT_PRICE` | Hold near target, concede slowly, demand a strong outcome |
| `FIRM` | Very small concessions, demanding acceptance threshold |
| `BALANCED` | Neutral defaults |
| `FLEXIBLE` | Concede readily, cautious opening |
| `FAST_SALE` | Open near target, move quickly toward the reservation limit |

The policy engine then converts the archetype into concrete, mandate-compliant terms.

---

## 3. Components and data flow

```
raw brief
   |
   v
PromptSanitizer -> IntakeExtractionService -> StructuredTermsDTO
   |                       |
   |                       +-- DeterministicIntakeExtractor (fallback/cross-check)
   |                       +-- per-field validation and outlier rejection
   v
NegotiationContext (read-only snapshot from Member A)
   |
   v
NegotiationAgentService
   |-- StrategySelector (Hybrid: learned + rule-based fallback)
   |-- NegotiationPolicyEngine (deterministic action/price)
   |-- MandateValidator + OfferConstraintValidator (hard boundary)
   |-- optional rationale LLM (prose only, validated against mandate leaks)
   v
ProposedOfferDTO
   |
   v
Member A maps to OfferDTO / deal state machine / persistence
```

Key classes:

| Class | Responsibility |
|---|---|
| `ai/negotiation/NegotiationAgentService` | Public facade: decide, draft counteroffer, select strategy |
| `ai/negotiation/NegotiationPolicyEngine` | Deterministic opening/counter/accept/stop logic |
| `ai/negotiation/MandateValidator` | Buyer/seller limit semantics and expiry |
| `ai/negotiation/OfferConstraintValidator` | Final hard-constraint boundary |
| `ai/negotiation/NegotiationContext` | Versioned read-only input snapshot |
| `ai/negotiation/ProposedOfferDTO` | Internal decision envelope (not a canonical contract) |
| `ai/strategy/StrategyFeatures` | Versioned 12-feature representation |
| `ai/strategy/StrategyModel` | Multinomial logistic regression artifact and inference |
| `ai/strategy/LearnedStrategySelector` | Fail-safe artifact loader and selector |
| `ai/strategy/HybridStrategySelector` | Learned-first with deterministic fallback |
| `ai/intake/IntakeExtractionService` | LLM + deterministic intake extraction |
| `ai/intake/DeterministicIntakeExtractor` | No-LLM extraction and cross-check |
| `ai/negotiation/PromptSanitizer` | Untrusted-text isolation and diagnostics |
| `ai/negotiation/RationaleValidator` | Blocks mandate leakage in LLM prose |
| `ai/client/OpenAiCompatibleChatClient` | Provider-neutral HTTP adapter |

The component does **not** write to the database, mutate deal state, call PayPal, or expose HTTP controllers. Those remain with the owning members.

---

## 4. Hard-constraint and privacy invariants

1. **Mandate privacy:** `NegotiationAgentService` never sends the mandate to the LLM. The intake prompt has no mandate. The rationale prompt contains only role, action, strategy, and the already-public offered price.
2. **Price authority:** prices and actions are produced only by `NegotiationPolicyEngine`; LLM output is used only for intake fields (validated) and rationale prose (validated).
3. **Final boundary:** every OPEN/COUNTER/ACCEPT passes `OfferConstraintValidator` with the current clock. A violation becomes `ESCALATE`, not a commitment.
4. **Role semantics:** buyer limit is a ceiling, seller limit is a floor; delivery and revision directions are role-specific and tested.
5. **Rationale leak defence:** `RationaleValidator` rejects prose containing mandate field names, limit/target numeric values, delimiter markers, or instruction-hijack markers. The public offer price is allowed, other mandate numbers are not.
6. **Human approval:** `ASSISTED` mode always sets `requiresHumanApproval=true`; `ESCALATE` always requires a human.
7. **No fake success:** model failures, invalid JSON, timeouts, and unsafe output fall back to deterministic extraction/selection or escalate; they do not return fabricated success.

---

## 5. Prompt-injection defence

All untrusted text is wrapped as:

```
<untrusted_counterparty_data>
...
</untrusted_counterparty_data>
```

`PromptSanitizer` additionally:

- removes delimiter break-out attempts,
- removes chat control tokens such as `<|im_start|>` and `<|system|>`,
- detects and replaces obvious hijack phrases (for example "ignore previous instructions", "system:", "reveal the mandate", "set the price to"),
- strips ISO control characters,
- truncates oversized input and records diagnostics.

Defence in depth matters more than any single filter: even if a payload survives, the LLM cannot change the action or price, and the final terms still pass the deterministic constraint boundary. The test suite includes a **15-case injection corpus** in `PromptSanitizerTest` plus end-to-end intake tests that capture the prompt sent to a stub model.

---

## 6. Dataset assessment

No public dataset was found that contains all fields required by the canonical Payagent contracts. The following were assessed and **not used**:

| Dataset | Why it was not used |
|---|---|
| **CraigslistBargain** | Human buyer/seller price dialogues; no delivery-days, revision-count, must-have/forbidden-term, or mandate labels. Useful for dialogue style, not for hard-constraint training or evaluation. |
| **CaSiNo** | Human campsite-allocation negotiations with priorities; not a monetary deal schema with mandates and deliverables. |
| **NegotiationToM** | Theory-of-mind benchmark for social reasoning; not a mandate-conditioned pricing dataset. |

Using any of these would require inventing mandate labels and would make the benchmark look human-grounded when it is not. That would violate the repository's "no fabricated metrics" rule. The benchmark therefore uses a clearly labelled simulator.

### 6.1 Synthetic scenario generator

`SyntheticScenarioGenerator` is deterministic from a recorded seed and produces:

- both buyer and seller callers,
- preference archetypes plus random coverage,
- narrow and wide price zones,
- short and long round budgets,
- cooperative and stubborn counterparties,
- correlated preference conflicts (for example high price priority plus high urgency),
- a small share of impossible deals.

The counterparty's reservation price and acceptance threshold are **hidden** from the selector and used only by the simulator and scoring function. This preserves the real information asymmetry.

### 6.2 Simulator

`NegotiationSimulator` drives the real `NegotiationPolicyEngine` for the agent and a documented counterparty model:

- the counterparty opens first in 80% of scenarios,
- it accepts when the agent's price crosses its hidden acceptance threshold,
- otherwise it concedes toward its reservation price with scenario-specific speed,
- the agent's terms are checked on every action.

The utility function combines price outcome and speed using the user's own `pricePriority` and `urgency`, with a fixed agreement component. It is an evaluation objective only; no product decision depends on it.

---

## 7. Model and training

| Item | Value |
|---|---|
| Model | Multinomial logistic regression |
| Features | 12 standardized features (`StrategyFeatures.SCHEMA_VERSION = 1`) |
| Classes | 5 negotiation strategies |
| Target | Soft distribution from simulated utility (`softmax(utility / τ)`) |
| Optimizer | Full-batch gradient descent |
| Training scenarios | 3,000 (seed `20261025`) |
| Evaluation scenarios | 1,000 (held-out seed `20261027`) |
| Epochs | 600 |
| Learning rate | 0.15 |
| L2 | 0.0005 |
| Soft-target temperature | 0.03 |
| Final training loss | 1.5246 |
| Artifact | `services/api/src/main/resources/ai/strategy-selector-v1.json` (4,948 bytes) |
| Artifact SHA-256 | `f6b9d047c0471351108a9a3f115439b8f1f5dab7aacb3013cf972d4f4340746c` |

The soft target is important: hard labels from a noisy simulated market would teach the model to imitate the most frequent winner rather than maximize expected utility. The reported `learnedExpectedUtility` (0.5777) is the probability-weighted diagnostic; the deployed selector uses the argmax, so `learnedPolicyUtility` (0.6007) is the relevant comparison.

The artifact stores its seed, feature schema version, label order, normalization vectors, weights, biases, deterministic metrics, and hyperparameters. Runtime latency and pipeline duration are report-only fields so that retraining produces a byte-identical artifact. It contains no user data, secrets, or large weights.

---

## 8. Benchmark results

### 8.1 Headline comparison (1,000 held-out scenarios)

| Metric | Value |
|---|---:|
| Learned policy utility (argmax) | **0.6007** |
| Rule-based baseline utility | **0.5565** |
| Improvement over rule-based | **+0.0442** (+7.9% relative) |
| Best fixed strategy utility (`FLEXIBLE`) | 0.5964 |
| Oracle (best strategy per scenario) | 0.6476 |
| Mean regret vs oracle | 0.0469 |
| Hard-best accuracy | 0.626 |
| Learned agreement rate | 0.863 |
| Rule-based agreement rate | 0.782 |
| Mean model confidence | 0.3091 |
| Inference latency | ~1.5 µs/prediction (report-only; omitted from the artifact to keep it deterministic) |
| Scenario generation + training + evaluation | ~0.8 s (report-only) |

`Hard-best accuracy` counts a prediction as correct when the selected strategy's simulated utility equals the best utility for that scenario within `1e-9`; ties count as correct.

### 8.2 Fixed-strategy comparison

| Strategy | Mean utility | Agreement rate |
|---|---:|---:|
| `PATIENT_PRICE` | 0.5324 | 0.705 |
| `FIRM` | 0.5024 | 0.657 |
| `BALANCED` | 0.5704 | 0.794 |
| `FLEXIBLE` | **0.5964** | 0.862 |
| `FAST_SALE` | 0.5927 | 0.898 |

### 8.3 Learned selection distribution

| Strategy | Selected |
|---|---:|
| `PATIENT_PRICE` | 170 |
| `FIRM` | 88 |
| `BALANCED` | 75 |
| `FLEXIBLE` | 104 |
| `FAST_SALE` | 563 |

The model does not collapse to a single constant strategy: all five archetypes are selected, with a majority toward fast sale in this synthetic market because agreement is common and speed is rewarded. The preference features still move the selection: the learned policy is above every fixed strategy, though only narrowly above `FLEXIBLE`.

### 8.4 Hybrid fallback threshold

`HybridStrategySelector` uses the learned model when its confidence is at least the configured threshold and otherwise falls back to the deterministic heuristic. The soft-target model is intentionally not overconfident, so the default threshold is `0.20`; the sweep below was measured on the same held-out set:

| Threshold | Hybrid utility | Learned use rate |
|---:|---:|---:|
| 0.00 | 0.6007 | 100% |
| 0.20 | 0.6007 | 100% |
| 0.25 | 0.5927 | 72.2% |
| 0.30 | 0.5865 | 42.0% |
| 0.35 | 0.5772 | 25.1% |
| 0.40 | 0.5707 | 17.0% |
| 0.50 | 0.5579 | 2.3% |

A high absolute-confidence threshold discards most learned decisions and reduces utility to the rule-based level. The model remains fail-safe: a missing or corrupt artifact, an unavailable selector, or a runtime error still falls back to the rule-based selector.

---

## 9. Interpretation and limitations

**What the results support:**

- The learned selector improves average simulated utility over the explainable rule-based baseline.
- It adapts across all five strategies rather than hard-coding one.
- Hard constraints are enforced independently of the model; the benchmark records zero non-compliant agent terms across the simulation tests.
- The selector adds roughly 1.5 microseconds of in-process latency per decision.

**What they do not support:**

- Human negotiation quality or real-market ROI.
- Superiority over `FLEXIBLE` by a large margin; the advantage over the best fixed strategy is small (0.6007 vs 0.5964).
- Robustness to adversarial counterparties beyond the tested injection corpus and synthetic model.
- Multilingual intake, non-USD settlement, or integration with Member A's persistence/state machine (not yet implemented in the repository).

The synthetic market, the rule-based baseline, and the utility function were designed by the same author. The benchmark is a reproducible engineering comparison, not an independent academic evaluation.

Other limitations:

- No `WAIT` action because there is no scheduler/resume path in the current application.
- No real LLM call is required for tests or the benchmark; LLM behavior is tested with deterministic stubs. A live provider/API key was not configured in this environment.
- `mandateCompliant` on `OfferDTO` is not used as a trust signal; the component recomputes compliance itself.
- The artifact is small by design. A larger learned policy may be justified after real deal data exists.

---

## 10. Reproducibility

Requirements: JDK 21 and the repository Maven wrapper. On this machine only JRE 25/JDK 11 were on `PATH`, so a local JDK 21 was selected through `JAVA_HOME`.

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"

cd services/api

# Full test suite (H2 profile; no database needed)
./mvnw -B test

# Build the runtime classpath once
./mvnw -B -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt

# Retrain and re-evaluate; this rewrites the committed artifact
java -cp "target/classes:$(cat target/cp.txt)" \
  com.payagent.ai.evaluation.StrategyModelTrainer \
  --train 3000 --eval 1000 \
  --seed 20261025 --eval-seed 20261027 \
  --epochs 600 --learning-rate 0.15 --l2 0.0005 --temperature 0.03 \
  --out src/main/resources/ai/strategy-selector-v1.json \
  --report target/ai-benchmark/report.json
```

The generator is deterministic from the seed, so the artifact and metrics are reproducible on the same JDK. The per-run latency figure naturally varies slightly.

The generated report is written to `services/api/target/ai-benchmark/report.json` (build output, not committed). The small model artifact is versioned under `src/main/resources` and must be committed with the code; it is currently untracked because no commit was requested.

---

## 11. Integration contract for Member A

The component is ready to be wired into the deal service; it does not add controllers or database code.

```java
// Snapshot built by Member A from persisted deal/offer state.
NegotiationContext context = NegotiationContext.of(
        dealId, callerRole, callerMandate, baseTerms, offerHistory, preferences);

// One decision: OPEN, COUNTER, ACCEPT, REJECT, ESCALATE, or STOP.
ProposedOfferDTO decision = negotiationAgentService.decide(context);

// Convenience overload when the counterparty's latest offer is supplied directly.
ProposedOfferDTO decision = negotiationAgentService.draftBoundedCounterOffer(
        context, counterpartyOffer);

// Intake extraction with deterministic fallback.
StructuredTermsDTO terms = intakeExtractionService.extractDealIntake(rawBrief, "USD");
```

Member A remains responsible for:

- authorizing the caller and loading only the caller's own mandate,
- mapping `ProposedOfferDTO` to the canonical `OfferDTO` and deal state machine,
- persisting offers, idempotency, and optimistic concurrency,
- enforcing `requiresHumanApproval` in `ASSISTED` mode,
- translating `AiNegotiationException.Code` to HTTP responses.

`ProposedOfferDTO` is intentionally **not** a canonical contract. `DealWorkspaceDTO.myMandate` remains the only REST/WebSocket place where a caller's mandate is serialized.

---

## 12. Verification checklist

| Check | Result |
|---|---|
| `./mvnw -B test` with JDK 21 | **PASS** — 84 tests, 0 failures, 0 errors, 1 skipped (existing release-gate placeholder) |
| Contract parity test | **PASS** — unchanged canonical contracts |
| Model artifact loads from classpath | **PASS** — `AiIntegrationTest` |
| Hard-constraint boundary tests | **PASS** — buyer ceiling, seller floor, delivery, revisions, required/forbidden terms, expiry |
| Prompt-injection corpus | **PASS** — 15 cases in `PromptSanitizerTest` |
| Intake deterministic/interrupt/fallback cases | **PASS** — invalid JSON, timeout, outlier price, outlier delivery, injection |
| Simulator determinism and zero constraint violations | **PASS** — `SyntheticEvaluationTest` |
| Benchmark reproducibility | **PASS** — seed and command recorded above |
| Secrets committed | **None** |
| Spring AI dependency added / POM changed | **No** (deliberate; see §2.2) |
| Real PayPal call | **NOT RUN** — out of scope for Member C |
| Live LLM API call | **NOT RUN** — no key configured; stubbed in tests |
| Human negotiation evaluation | **NOT RUN** — no suitable dataset; synthetic benchmark only |

---

## 13. Artifact and source map

```
services/api/src/main/java/com/payagent/ai/
├── AiNegotiationException.java
├── client/
│   ├── ChatCompletionClient.java
│   ├── LlmCallException.java
│   └── OpenAiCompatibleChatClient.java
├── intake/
│   ├── DeterministicIntakeExtractor.java
│   ├── IntakeExtractionResult.java
│   └── IntakeExtractionService.java
├── negotiation/
│   ├── MandateValidator.java
│   ├── MoneyParser.java
│   ├── NegotiationAction.java
│   ├── NegotiationAgentService.java
│   ├── NegotiationContext.java
│   ├── NegotiationPolicyEngine.java
│   ├── NegotiationPreferences.java
│   ├── NegotiationStrategy.java
│   ├── OfferConstraintValidator.java
│   ├── PolicyDecision.java
│   ├── PromptSanitizer.java
│   ├── ProposedOfferDTO.java
│   ├── RationaleValidator.java
│   └── StructuredTermsValidator.java
├── strategy/
│   ├── HybridStrategySelector.java
│   ├── LearnedStrategySelector.java
│   ├── RuleBasedStrategySelector.java
│   ├── StrategyFeatures.java
│   ├── StrategyModel.java
│   ├── StrategySelection.java
│   └── StrategySelector.java
└── evaluation/
    ├── NegotiationSimulator.java
    ├── ScenarioUtility.java
    ├── SimulationResult.java
    ├── StrategyModelTrainer.java
    ├── SyntheticScenario.java
    └── SyntheticScenarioGenerator.java

services/api/src/main/resources/ai/strategy-selector-v1.json
services/api/src/test/java/com/payagent/ai/**
```
