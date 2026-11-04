package com.payagent.ai.evaluation;

import com.payagent.common.contracts.PartyRole;

/**
 * Utility used for strategy comparison.
 *
 * <p>It combines price outcome and speed using the user's own dials, and gives a
 * fixed bonus for reaching an agreement. It is an evaluation objective, not a
 * canonical financial value; no product decision depends on it.</p>
 */
public final class ScenarioUtility {

    private ScenarioUtility() {}

    public static double score(
            SyntheticScenario scenario,
            boolean dealReached,
            long finalPriceMinorUnits,
            int rounds) {
        if (!dealReached) {
            return 0.0;
        }
        double priceUtility = priceUtility(scenario, finalPriceMinorUnits);
        double speedUtility = speedUtility(scenario, rounds);
        double priceWeight = 0.25 + 0.50 * scenario.preferences().pricePriority();
        double speedWeight = 0.15 + 0.50 * scenario.preferences().urgency();
        double agreementWeight = 0.25;
        double totalWeight = priceWeight + speedWeight + agreementWeight;
        return clamp01((priceWeight * priceUtility
                + speedWeight * speedUtility
                + agreementWeight) / totalWeight);
    }

    private static double priceUtility(SyntheticScenario scenario, long price) {
        long limit = scenario.mandate().limitPriceMinorUnits();
        long target = scenario.mandate().targetPriceMinorUnits();
        if (scenario.callerRole() == PartyRole.BUYER) {
            long zone = Math.max(1L, limit - target);
            return clamp01((double) (limit - price) / zone);
        }
        long zone = Math.max(1L, target - limit);
        return clamp01((double) (price - limit) / zone);
    }

    private static double speedUtility(SyntheticScenario scenario, int rounds) {
        int maxRounds = Math.max(1, scenario.maxRounds());
        if (maxRounds == 1) {
            return 1.0;
        }
        return clamp01(1.0 - (double) (Math.max(1, rounds) - 1) / (maxRounds - 1));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
