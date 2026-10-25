package com.payagent.ai.evaluation;

import com.payagent.ai.negotiation.NegotiationAction;
import com.payagent.ai.negotiation.NegotiationStrategy;

import java.util.List;

/** Outcome of one simulated negotiation under a fixed strategy. */
public record SimulationResult(
        String scenarioId,
        NegotiationStrategy strategy,
        boolean dealReached,
        long finalPriceMinorUnits,
        int rounds,
        int constraintViolations,
        double utility,
        List<NegotiationAction> actions) {

    public SimulationResult {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
