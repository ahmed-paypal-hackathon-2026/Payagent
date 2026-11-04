package com.payagent.ai.negotiation;

import com.payagent.ai.AiNegotiationException;

import java.util.ArrayList;
import java.util.List;

/**
 * User-controlled negotiation preferences.
 *
 * <p>The four dials are independent in the API but are combined by an explicit
 * weighted objective in {@link com.payagent.ai.strategy.StrategyFeatures}. The
 * representation deliberately avoids per-item hard-coded rules: item urgency is
 * expressed through {@link #urgency()} rather than through an item category.</p>
 *
 * @param pricePriority          0 = complete the sale quickly, 1 = maximize price
 * @param urgency                0 = can wait for a better offer, 1 = must close quickly
 * @param concessionTolerance    0 = firm, 1 = accepts larger/faster concessions
 * @param openingAggressiveness  0 = cautious opening, 1 = aggressive opening
 */
public record NegotiationPreferences(
        double pricePriority,
        double urgency,
        double concessionTolerance,
        double openingAggressiveness) {

    public static final NegotiationPreferences DEFAULT =
            new NegotiationPreferences(0.5, 0.35, 0.5, 0.5);

    public NegotiationPreferences {
        requireUnitInterval("pricePriority", pricePriority);
        requireUnitInterval("urgency", urgency);
        requireUnitInterval("concessionTolerance", concessionTolerance);
        requireUnitInterval("openingAggressiveness", openingAggressiveness);
    }

    public static NegotiationPreferences of(
            double pricePriority,
            double urgency,
            double concessionTolerance,
            double openingAggressiveness) {
        return new NegotiationPreferences(
                pricePriority, urgency, concessionTolerance, openingAggressiveness);
    }

    public static NegotiationPreferences priceMaximizing() {
        return new NegotiationPreferences(0.95, 0.1, 0.25, 0.8);
    }

    public static NegotiationPreferences fastSale() {
        return new NegotiationPreferences(0.1, 0.95, 0.9, 0.2);
    }

    /**
     * Both dials being extreme is not rejected because both can be legitimate
     * (a fast sale at a good price), but the selector records it so the UI can
     * explain the trade-off instead of silently pretending the conflict is absent.
     */
    public List<String> warnings() {
        List<String> warnings = new ArrayList<>();
        if (pricePriority >= 0.75 && urgency >= 0.75) {
            warnings.add("PRICE_AND_SPEED_BOTH_HIGH: preferences conflict; policy will balance both explicitly");
        }
        if (concessionTolerance <= 0.1 && urgency >= 0.8) {
            warnings.add("FIRM_AND_URGENT: low concession tolerance may prevent a quick close");
        }
        return warnings;
    }

    private static void requireUnitInterval(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new AiNegotiationException(
                    AiNegotiationException.Code.INVALID_PREFERENCES,
                    name + " must be finite and between 0 and 1");
        }
    }
}
