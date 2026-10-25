package com.payagent.ai.strategy;

import com.payagent.ai.negotiation.NegotiationStrategy;

import java.util.EnumMap;
import java.util.Map;

/** Result of strategy selection, with the diagnostic trail needed for explainability. */
public record StrategySelection(
        NegotiationStrategy strategy,
        double confidence,
        Source source,
        Map<NegotiationStrategy, Double> probabilities,
        String note) {

    public enum Source {
        LEARNED,
        RULE_BASED,
        DEFAULT
    }

    public StrategySelection {
        if (strategy == null) {
            throw new IllegalArgumentException("strategy is required");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be finite and between 0 and 1");
        }
        EnumMap<NegotiationStrategy, Double> copy = new EnumMap<>(NegotiationStrategy.class);
        if (probabilities != null) {
            copy.putAll(probabilities);
        }
        probabilities = Map.copyOf(copy);
    }
}
