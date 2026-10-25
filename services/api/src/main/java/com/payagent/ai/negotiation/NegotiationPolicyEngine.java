package com.payagent.ai.negotiation;

import com.payagent.ai.AiNegotiationException;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic negotiation behaviour. The LLM never sets prices; this engine
 * computes every candidate action and price from the user's mandate, explicit
 * strategy and offer history, then validates the result at the decision boundary.
 */
@Service
public class NegotiationPolicyEngine {

    static final long MIN_PRICE_MINOR_UNITS = 0L;

    private final MandateValidator mandateValidator = new MandateValidator();
    private final OfferConstraintValidator constraintValidator = new OfferConstraintValidator();

    public PolicyDecision decide(
            NegotiationContext context,
            NegotiationStrategy strategy,
            Instant now) {
        mandateValidator.validate(context.mandate(), now);
        if (strategy == null) {
            throw new AiNegotiationException(
                    AiNegotiationException.Code.INVALID_STATE, "strategy is required");
        }

        MandateDTO mandate = context.mandate();
        OfferDTOView latest = OfferDTOView.of(context.latestCounterpartyOffer());
        int nextRound = context.nextRoundNumber();
        boolean finalTurn = nextRound > mandate.maxRounds();

        if (latest == null) {
            long price = openingPrice(context, strategy);
            return buildCounterDecision(
                    context, strategy, context.baseTerms(), price,
                    NegotiationAction.OPEN,
                    "Opening " + strategy.displayName().toLowerCase(Locale.ROOT)
                            + " offer at " + money(price, mandate.currency())
                            + "; the scope is preserved and the price is inside the user's authorized range.",
                    List.of(),
                    "OPENING_OFFER",
                    false,
                    now);
        }

        long plannedCounter = plannedCounterPrice(context, strategy, nextRound, latest.priceMinorUnits());
        long acceptanceThreshold = acceptancePrice(context, strategy);
        boolean hardCompliant = constraintValidator
                .inspect(context.callerRole(), mandate, latest.terms(), now)
                .isEmpty();

        boolean betterThanCounter = context.callerRole() == PartyRole.SELLER
                ? latest.priceMinorUnits() >= plannedCounter
                : latest.priceMinorUnits() <= plannedCounter;
        boolean meetsThreshold = context.callerRole() == PartyRole.SELLER
                ? latest.priceMinorUnits() >= acceptanceThreshold
                : latest.priceMinorUnits() <= acceptanceThreshold;

        if (finalTurn) {
            boolean relaxed = relaxationAllows(context, strategy, latest.priceMinorUnits());
            if (hardCompliant && (meetsThreshold || betterThanCounter || relaxed)) {
                return new PolicyDecision(
                        NegotiationAction.ACCEPT,
                        latest.terms(),
                        "Accepting at " + money(latest.priceMinorUnits(), mandate.currency())
                                + " on the final round because the offer is within the user's authorized range"
                                + " and satisfies the " + strategy.displayName().toLowerCase(Locale.ROOT)
                                + " acceptance rule.",
                        List.of(),
                        "ACCEPT_FINAL_ROUND",
                        false);
            }
            return new PolicyDecision(
                    NegotiationAction.STOP,
                    null,
                    "No agreement within the configured rounds. The last offer was not accepted because it"
                            + " did not satisfy the mandate or the selected acceptance rule.",
                    hardCompliant ? List.of() : List.of("LAST_OFFER_OUTSIDE_MANDATE"),
                    hardCompliant ? "MAX_ROUNDS_REACHED" : "POLICY_VIOLATION_BLOCKED",
                    false);
        }

        if (hardCompliant && (meetsThreshold || betterThanCounter)) {
            return new PolicyDecision(
                    NegotiationAction.ACCEPT,
                    latest.terms(),
                    "Accepting at " + money(latest.priceMinorUnits(), mandate.currency())
                            + " because the offer is within the user's authorized range and satisfies the "
                            + strategy.displayName().toLowerCase(Locale.ROOT) + " acceptance rule.",
                    List.of(),
                    "ACCEPT_WITHIN_POLICY",
                    false);
        }

        List<String> warnings = new ArrayList<>();
        if (!hardCompliant) {
            warnings.add("COUNTERPARTY_TERMS_VIOLATED_MANDATE");
        }

        long counterPrice = settleCounterPrice(
                context, plannedCounter, latest.priceMinorUnits());
        StructuredTermsDTO counterTerms = buildTerms(context, latest.terms(), counterPrice);
        List<OfferConstraintValidator.Violation> violations = constraintValidator
                .inspect(context.callerRole(), mandate, counterTerms, now);
        if (!violations.isEmpty()) {
            counterTerms = buildTerms(context, context.baseTerms(), counterPrice);
            violations = constraintValidator.inspect(context.callerRole(), mandate, counterTerms, now);
            if (!violations.isEmpty()) {
                return new PolicyDecision(
                        NegotiationAction.ESCALATE,
                        null,
                        "Escalating to the user: a counteroffer satisfying the mandate could not be produced"
                                + " from the available scope.",
                        List.of("NO_SAFE_COUNTEROFFER"),
                        "POLICY_VIOLATION_BLOCKED",
                        true);
            }
            warnings.add("COUNTEROFFER_REBUILT_FROM_BASE_TERMS");
        }

        return buildCounterDecision(
                context, strategy, counterTerms, counterPrice,
                NegotiationAction.COUNTER,
                "Countering at " + money(counterPrice, mandate.currency()) + " with "
                        + strategy.displayName().toLowerCase(Locale.ROOT)
                        + " pacing. The offer stays within the user's authorized range.",
                warnings,
                "COUNTEROFFER_WITHIN_POLICY",
                !warnings.isEmpty(),
                now);
    }

    private PolicyDecision buildCounterDecision(
            NegotiationContext context,
            NegotiationStrategy strategy,
            StructuredTermsDTO sourceTerms,
            long price,
            NegotiationAction action,
            String rationale,
            List<String> warnings,
            String reasonCode,
            boolean fallbackUsed,
            Instant now) {
        StructuredTermsDTO terms = buildTerms(context, sourceTerms, price);
        List<OfferConstraintValidator.Violation> violations = constraintValidator.inspect(
                context.callerRole(), context.mandate(), terms, now);
        if (!violations.isEmpty()) {
            return new PolicyDecision(
                    NegotiationAction.ESCALATE,
                    null,
                    "Escalating to the user: generated terms failed the mandate validation boundary.",
                    List.of("FINAL_TERMS_REJECTED"),
                    "POLICY_VIOLATION_BLOCKED",
                    true);
        }
        return new PolicyDecision(action, terms, rationale, warnings, reasonCode, fallbackUsed);
    }

    long openingPrice(NegotiationContext context, NegotiationStrategy strategy) {
        MandateDTO mandate = context.mandate();
        long limit = mandate.limitPriceMinorUnits();
        long target = mandate.targetPriceMinorUnits();
        long zone = Math.abs(limit - target);
        double effectiveAggression = effectiveOpeningAggression(context, strategy);
        long offset = Math.round(effectiveAggression * 0.5 * zone);
        long price;
        if (context.callerRole() == PartyRole.SELLER) {
            price = target + offset;
        } else {
            price = target - offset;
        }
        return clampPrice(context, price);
    }

    long plannedCounterPrice(
            NegotiationContext context,
            NegotiationStrategy strategy,
            int turn,
            long counterpartyPrice) {
        MandateDTO mandate = context.mandate();
        long limit = mandate.limitPriceMinorUnits();
        long opening = openingPrice(context, strategy);
        int maxRounds = Math.max(1, mandate.maxRounds());
        int curveTurn = Math.min(Math.max(1, turn), maxRounds);
        double progress = progress(curveTurn, maxRounds, effectiveRate(context, strategy));
        double maxConcession = effectiveMaxConcession(context, strategy);
        double concession = maxConcession * progress;
        double planned = opening + concession * (limit - opening);
        long price = Math.round(planned);
        price = clampPrice(context, price);
        price = enforceMonotonic(context, price);
        price = avoidCrossingCounterparty(context, price, counterpartyPrice);
        return clampPrice(context, price);
    }

    private long settleCounterPrice(
            NegotiationContext context,
            long plannedPrice,
            long counterpartyPrice) {
        long price = clampPrice(context, plannedPrice);
        price = enforceMonotonic(context, price);
        price = avoidCrossingCounterparty(context, price, counterpartyPrice);
        return clampPrice(context, price);
    }

    long acceptancePrice(NegotiationContext context, NegotiationStrategy strategy) {
        MandateDTO mandate = context.mandate();
        long limit = mandate.limitPriceMinorUnits();
        long target = mandate.targetPriceMinorUnits();
        long zone = Math.abs(limit - target);
        double share = strategy.acceptanceShare();
        share *= 1.0 - 0.45 * context.preferences().urgency();
        share *= 0.75 + 0.45 * context.preferences().pricePriority();
        share = Math.max(0.05, Math.min(0.95, share));
        long offset = Math.round(share * zone);
        return context.callerRole() == PartyRole.SELLER ? limit + offset : limit - offset;
    }

    StructuredTermsDTO buildTerms(
            NegotiationContext context,
            StructuredTermsDTO source,
            long price) {
        MandateDTO mandate = context.mandate();
        StructuredTermsDTO base = context.baseTerms();
        StructuredTermsDTO safeSource = source == null ? base : source;

        boolean forbiddenInSource = containsForbidden(safeSource, mandate);
        StructuredTermsDTO scopeSource = forbiddenInSource ? base : safeSource;

        String title = firstNonBlank(scopeSource.title(), base.title(), "Untitled negotiation");
        String acceptance = firstNonBlank(
                scopeSource.acceptanceCriteriaText(),
                base.acceptanceCriteriaText(),
                "The buyer confirms that all listed deliverables are complete.");

        List<String> deliverables = new ArrayList<>();
        addUnique(deliverables, scopeSource.deliverables());
        if (deliverables.isEmpty()) {
            addUnique(deliverables, base.deliverables());
        }
        for (String required : mandate.mustHaveDeliverables()) {
            boolean present = deliverables.stream()
                    .anyMatch(d -> d.toLowerCase(Locale.ROOT).contains(required.toLowerCase(Locale.ROOT)));
            if (!present) {
                addUnique(deliverables, List.of(required));
            }
        }

        int deliveryDays;
        int revisions;
        if (context.callerRole() == PartyRole.SELLER) {
            deliveryDays = Math.max(safeDelivery(scopeSource.deliveryDays()), mandate.limitDeliveryDays());
            revisions = Math.min(Math.max(0, scopeSource.includedRevisions()), mandate.limitRevisions());
        } else {
            deliveryDays = Math.min(safeDelivery(scopeSource.deliveryDays()), mandate.limitDeliveryDays());
            revisions = Math.max(Math.max(0, scopeSource.includedRevisions()), mandate.limitRevisions());
        }

        return new StructuredTermsDTO(
                title,
                List.copyOf(deliverables),
                Math.max(MIN_PRICE_MINOR_UNITS, price),
                mandate.currency(),
                Math.max(1, deliveryDays),
                Math.max(0, revisions),
                acceptance);
    }

    private boolean relaxationAllows(
            NegotiationContext context,
            NegotiationStrategy strategy,
            long counterpartyPrice) {
        if (context.preferences().urgency() < 0.5) {
            return false;
        }
        MandateDTO mandate = context.mandate();
        long zone = Math.abs(mandate.limitPriceMinorUnits() - mandate.targetPriceMinorUnits());
        long slack = Math.round(0.20 * zone);
        long threshold = acceptancePrice(context, strategy);
        if (context.callerRole() == PartyRole.SELLER) {
            return counterpartyPrice >= Math.max(mandate.limitPriceMinorUnits(), threshold - slack);
        }
        return counterpartyPrice <= Math.min(mandate.limitPriceMinorUnits(), threshold + slack);
    }

    private long enforceMonotonic(NegotiationContext context, long price) {
        OfferDTOView previous = OfferDTOView.of(context.latestOwnOffer());
        if (previous == null) {
            return price;
        }
        if (context.callerRole() == PartyRole.SELLER) {
            return Math.min(price, previous.priceMinorUnits());
        }
        return Math.max(price, previous.priceMinorUnits());
    }

    private long avoidCrossingCounterparty(
            NegotiationContext context,
            long price,
            long counterpartyPrice) {
        if (context.callerRole() == PartyRole.SELLER) {
            return Math.max(price, counterpartyPrice);
        }
        return Math.min(price, counterpartyPrice);
    }

    private long clampPrice(NegotiationContext context, long price) {
        MandateDTO mandate = context.mandate();
        long limit = mandate.limitPriceMinorUnits();
        long target = mandate.targetPriceMinorUnits();
        long lower;
        long upper;
        if (context.callerRole() == PartyRole.SELLER) {
            lower = limit;
            upper = Math.max(limit, target + Math.abs(target - limit));
        } else {
            lower = MIN_PRICE_MINOR_UNITS;
            upper = limit;
        }
        return Math.max(lower, Math.min(upper, price));
    }

    private double effectiveOpeningAggression(
            NegotiationContext context,
            NegotiationStrategy strategy) {
        return clamp01(0.5 * strategy.openingAggressiveness()
                + 0.5 * context.preferences().openingAggressiveness());
    }

    private double effectiveMaxConcession(
            NegotiationContext context,
            NegotiationStrategy strategy) {
        double value = strategy.maxConcessionFraction()
                * (0.70 + 0.60 * context.preferences().concessionTolerance());
        return Math.max(0.05, Math.min(0.98, value));
    }

    private double effectiveRate(
            NegotiationContext context,
            NegotiationStrategy strategy) {
        double value = strategy.concessionRate()
                * (0.65 + 0.70 * context.preferences().urgency())
                * (0.75 + 0.50 * context.preferences().concessionTolerance());
        return Math.max(0.05, Math.min(3.0, value));
    }

    private static double progress(int turn, int maxRounds, double rate) {
        if (turn <= 1 || maxRounds <= 1) {
            return 0.0;
        }
        double raw = 1.0 - Math.exp(-rate * (turn - 1));
        double normalization = 1.0 - Math.exp(-rate * (maxRounds - 1));
        if (normalization <= 0.0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, raw / normalization));
    }

    private static boolean containsForbidden(StructuredTermsDTO terms, MandateDTO mandate) {
        String scope = (nullSafe(terms.title()) + "\n" + String.join("\n", safeList(terms.deliverables()))
                + "\n" + nullSafe(terms.acceptanceCriteriaText())).toLowerCase(Locale.ROOT);
        for (String forbidden : mandate.forbiddenTerms()) {
            if (scope.contains(forbidden.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static void addUnique(List<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()
                    && target.stream().noneMatch(existing -> existing.equalsIgnoreCase(value.trim()))) {
                target.add(value.trim());
            }
        }
    }

    private static int safeDelivery(int deliveryDays) {
        return deliveryDays < 1 ? 1 : deliveryDays;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String money(long minorUnits, String currency) {
        return MoneyParser.formatMajorUnits(minorUnits) + " " + currency;
    }

    /** Null-safe view used to keep policy arithmetic explicit. */
    private record OfferDTOView(long priceMinorUnits, StructuredTermsDTO terms) {
        static OfferDTOView of(com.payagent.common.contracts.OfferDTO offer) {
            if (offer == null) {
                return null;
            }
            if (offer.terms() == null) {
                throw new AiNegotiationException(
                        AiNegotiationException.Code.INVALID_STATE,
                        "offer " + offer.offerId() + " has no terms");
            }
            return new OfferDTOView(offer.terms().priceMinorUnits(), offer.terms());
        }
    }
}
