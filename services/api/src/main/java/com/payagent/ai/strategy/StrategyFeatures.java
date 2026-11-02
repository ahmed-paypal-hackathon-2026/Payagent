package com.payagent.ai.strategy;

import com.payagent.ai.AiNegotiationException;
import com.payagent.ai.negotiation.MandateValidator;
import com.payagent.ai.negotiation.NegotiationContext;
import com.payagent.ai.negotiation.NegotiationPreferences;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.PartyRole;

import java.util.List;

/**
 * Versioned feature representation available at strategy-selection time.
 *
 * <p>Only information legitimately available before the negotiation begins is
 * used: mandate limits, role, round budget and user preferences. No outcome,
 * counterparty reservation or future offer is included, which prevents target
 * leakage.</p>
 */
public record StrategyFeatures(
        double pricePriority,
        double urgency,
        double concessionTolerance,
        double openingAggressiveness,
        double zoneRatio,
        double roundBudget,
        double roleSeller,
        double priceXUrgency,
        double toleranceXUrgency,
        double aggressivenessXTolerance,
        double sellerPrice,
        double sellerUrgency) {

    public static final int SCHEMA_VERSION = 1;

    public static final List<String> NAMES = List.of(
            "pricePriority",
            "urgency",
            "concessionTolerance",
            "openingAggressiveness",
            "zoneRatio",
            "roundBudget",
            "roleSeller",
            "priceXUrgency",
            "toleranceXUrgency",
            "aggressivenessXTolerance",
            "sellerPrice",
            "sellerUrgency");

    public static StrategyFeatures from(NegotiationContext context) {
        return from(context.callerRole(), context.mandate(), context.preferences());
    }

    public static StrategyFeatures from(
            PartyRole role,
            MandateDTO mandate,
            NegotiationPreferences preferences) {
        if (role == null || preferences == null) {
            throw new AiNegotiationException(
                    AiNegotiationException.Code.INVALID_STATE,
                    "role and preferences are required for strategy features");
        }
        new MandateValidator().validate(mandate);
        double limit = mandate.limitPriceMinorUnits();
        double target = mandate.targetPriceMinorUnits();
        double scale = Math.max(1.0, Math.max(limit, target));
        double zoneRatio = clamp01(Math.abs(limit - target) / scale);
        double roundBudget = clamp01(mandate.maxRounds() / 12.0);
        double roleSeller = role == PartyRole.SELLER ? 1.0 : 0.0;
        double price = preferences.pricePriority();
        double urgency = preferences.urgency();
        double tolerance = preferences.concessionTolerance();
        double aggression = preferences.openingAggressiveness();

        return new StrategyFeatures(
                price,
                urgency,
                tolerance,
                aggression,
                zoneRatio,
                roundBudget,
                roleSeller,
                price * urgency,
                tolerance * urgency,
                aggression * tolerance,
                roleSeller * price,
                roleSeller * urgency);
    }

    public double[] toVector() {
        return new double[] {
                pricePriority,
                urgency,
                concessionTolerance,
                openingAggressiveness,
                zoneRatio,
                roundBudget,
                roleSeller,
                priceXUrgency,
                toleranceXUrgency,
                aggressivenessXTolerance,
                sellerPrice,
                sellerUrgency
        };
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
