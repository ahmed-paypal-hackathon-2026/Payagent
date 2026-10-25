package com.payagent.ai.strategy;

import com.payagent.ai.negotiation.NegotiationContext;
import com.payagent.ai.negotiation.NegotiationPreferences;
import com.payagent.ai.negotiation.NegotiationStrategy;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Explainable baseline selector. It is deliberately simple and is used as a
 * fallback when the learned model is unavailable or not confident.
 */
@Component
public class RuleBasedStrategySelector implements StrategySelector {

    private static final double TEMPERATURE = 0.55;

    @Override
    public StrategySelection select(NegotiationContext context) {
        NegotiationPreferences p = context.preferences();
        double price = p.pricePriority();
        double urgency = p.urgency();
        double tolerance = p.concessionTolerance();
        double aggression = p.openingAggressiveness();

        Map<NegotiationStrategy, Double> scores = new EnumMap<>(NegotiationStrategy.class);
        scores.put(NegotiationStrategy.PATIENT_PRICE,
                1.35 * price + 0.45 * (1 - urgency) + 0.30 * (1 - tolerance) + 0.15 * aggression);
        scores.put(NegotiationStrategy.FIRM,
                1.15 * price + 0.35 * (1 - urgency) + 0.85 * (1 - tolerance) + 0.65 * aggression);
        scores.put(NegotiationStrategy.BALANCED,
                1.00 - 0.85 * (Math.abs(price - 0.5) + Math.abs(urgency - 0.5)
                        + Math.abs(tolerance - 0.5) + Math.abs(aggression - 0.5)) / 2.0);
        scores.put(NegotiationStrategy.FLEXIBLE,
                0.55 * (1 - price) + 0.40 * urgency + 1.05 * tolerance + 0.25 * (1 - aggression));
        scores.put(NegotiationStrategy.FAST_SALE,
                1.20 * (1 - price) + 1.30 * urgency + 0.65 * tolerance + 0.45 * (1 - aggression));

        return softmax(scores);
    }

    private StrategySelection softmax(Map<NegotiationStrategy, Double> scores) {
        double max = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double sum = 0.0;
        Map<NegotiationStrategy, Double> probabilities = new EnumMap<>(NegotiationStrategy.class);
        for (Map.Entry<NegotiationStrategy, Double> entry : scores.entrySet()) {
            double value = Math.exp((entry.getValue() - max) / TEMPERATURE);
            probabilities.put(entry.getKey(), value);
            sum += value;
        }
        for (Map.Entry<NegotiationStrategy, Double> entry : probabilities.entrySet()) {
            probabilities.put(entry.getKey(), entry.getValue() / sum);
        }
        Map.Entry<NegotiationStrategy, Double> best = probabilities.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();
        return new StrategySelection(
                best.getKey(), best.getValue(), StrategySelection.Source.RULE_BASED,
                probabilities, "deterministic preference heuristic");
    }
}
