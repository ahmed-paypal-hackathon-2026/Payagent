package com.payagent.ai.strategy;

import com.payagent.ai.negotiation.NegotiationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Uses the learned selector when it is available and confident, and otherwise
 * falls back to the deterministic heuristic. This keeps adaptive behaviour
 * measurable without making availability of a trained artifact a hard runtime
 * dependency.
 */
@Component
@Primary
public class HybridStrategySelector implements StrategySelector {

    private static final Logger log = LoggerFactory.getLogger(HybridStrategySelector.class);

    private final RuleBasedStrategySelector ruleBased;
    private final LearnedStrategySelector learned;
    private final double confidenceThreshold;

    public HybridStrategySelector(
            RuleBasedStrategySelector ruleBased,
            LearnedStrategySelector learned,
            @Value("${payagent.ai.learned-confidence-threshold:0.20}") double confidenceThreshold) {
        if (!Double.isFinite(confidenceThreshold) || confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
            throw new IllegalArgumentException("confidence threshold must be between 0 and 1");
        }
        this.ruleBased = ruleBased;
        this.learned = learned;
        this.confidenceThreshold = confidenceThreshold;
    }

    @Override
    public StrategySelection select(NegotiationContext context) {
        if (learned.isAvailable()) {
            try {
                StrategySelection learnedSelection = learned.select(context);
                if (learnedSelection.confidence() >= confidenceThreshold) {
                    return learnedSelection;
                }
                StrategySelection fallback = ruleBased.select(context);
                log.debug("Learned strategy confidence {} below {}; using rule-based fallback",
                        learnedSelection.confidence(), confidenceThreshold);
                return new StrategySelection(
                        fallback.strategy(),
                        fallback.confidence(),
                        StrategySelection.Source.RULE_BASED,
                        fallback.probabilities(),
                        "learned confidence below threshold; deterministic fallback");
            } catch (RuntimeException ex) {
                log.debug("Learned strategy selector failed ({}); using rule-based fallback",
                        ex.getClass().getSimpleName());
            }
        }
        return ruleBased.select(context);
    }

    public double confidenceThreshold() {
        return confidenceThreshold;
    }
}
