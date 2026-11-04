package com.payagent.ai.negotiation;

import org.springframework.stereotype.Component;
import com.payagent.ai.AiNegotiationException;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hard, deterministic mandate enforcement at the final decision boundary.
 *
 * <p>This validator is intentionally independent of any LLM. Every price,
 * acceptance and counteroffer produced by the AI component passes through it
 * before being returned to the application. An LLM can influence wording but
 * cannot widen a user's limits.</p>
 */
@Component
public final class OfferConstraintValidator {

    public enum Code {
        MISSING_TERMS,
        INVALID_PRICE,
        CURRENCY_MISMATCH,
        INVALID_DELIVERY_DAYS,
        INVALID_REVISIONS,
        EMPTY_DELIVERABLES,
        BUYER_PRICE_LIMIT_EXCEEDED,
        SELLER_PRICE_FLOOR_VIOLATED,
        BUYER_DELIVERY_LIMIT_EXCEEDED,
        SELLER_DELIVERY_MINIMUM_VIOLATED,
        BUYER_REVISIONS_MINIMUM_VIOLATED,
        SELLER_REVISIONS_MAXIMUM_VIOLATED,
        REQUIRED_DELIVERABLE_MISSING,
        FORBIDDEN_TERM_PRESENT,
        MANDATE_EXPIRED
    }

    public record Violation(Code code, String field, String message) {}

    private final MandateValidator mandateValidator = new MandateValidator();

    public List<Violation> inspect(
            PartyRole callerRole,
            MandateDTO mandate,
            StructuredTermsDTO terms) {
        return inspect(callerRole, mandate, terms, null);
    }

    /**
     * @param now optional clock used to check mandate expiry; {@code null} skips
     *            the expiry check (useful for stateless schema inspection)
     */
    public List<Violation> inspect(
            PartyRole callerRole,
            MandateDTO mandate,
            StructuredTermsDTO terms,
            java.time.Instant now) {
        List<Violation> violations = new ArrayList<>();
        if (callerRole == null || mandate == null) {
            violations.add(new Violation(
                    Code.MISSING_TERMS, "mandate", "caller role and mandate are required"));
            return violations;
        }
        if (terms == null) {
            violations.add(new Violation(Code.MISSING_TERMS, "terms", "terms are missing"));
            return violations;
        }

        if (now != null) {
            try {
                mandateValidator.validate(mandate, now);
            } catch (AiNegotiationException ex) {
                Code code = ex.code() == AiNegotiationException.Code.MANDATE_EXPIRED
                        ? Code.MANDATE_EXPIRED
                        : Code.MISSING_TERMS;
                violations.add(new Violation(code, "mandate", ex.getMessage()));
                return violations;
            }
        }

        if (terms.title() == null || terms.title().isBlank()) {
            violations.add(new Violation(Code.MISSING_TERMS, "title", "title is required"));
        }
        if (terms.deliverables() == null || terms.deliverables().isEmpty()) {
            violations.add(new Violation(
                    Code.EMPTY_DELIVERABLES, "deliverables", "at least one deliverable is required"));
        }
        if (terms.currency() == null || !terms.currency().equalsIgnoreCase(mandate.currency())) {
            violations.add(new Violation(
                    Code.CURRENCY_MISMATCH, "currency", "currency does not match the mandate"));
        }
        if (terms.priceMinorUnits() < 0) {
            violations.add(new Violation(Code.INVALID_PRICE, "priceMinorUnits", "price must not be negative"));
        }
        if (terms.deliveryDays() < 1) {
            violations.add(new Violation(
                    Code.INVALID_DELIVERY_DAYS, "deliveryDays", "delivery days must be at least 1"));
        }
        if (terms.includedRevisions() < 0) {
            violations.add(new Violation(
                    Code.INVALID_REVISIONS, "includedRevisions", "revisions must not be negative"));
        }

        if (callerRole == PartyRole.BUYER) {
            if (terms.priceMinorUnits() > mandate.limitPriceMinorUnits()) {
                violations.add(new Violation(
                        Code.BUYER_PRICE_LIMIT_EXCEEDED,
                        "priceMinorUnits",
                        "buyer offer exceeds the authorized ceiling"));
            }
            if (terms.deliveryDays() > mandate.limitDeliveryDays()) {
                violations.add(new Violation(
                        Code.BUYER_DELIVERY_LIMIT_EXCEEDED,
                        "deliveryDays",
                        "buyer delivery exceeds the authorized maximum"));
            }
            if (terms.includedRevisions() < mandate.limitRevisions()) {
                violations.add(new Violation(
                        Code.BUYER_REVISIONS_MINIMUM_VIOLATED,
                        "includedRevisions",
                        "buyer offer is below the authorized minimum revisions"));
            }
        } else {
            if (terms.priceMinorUnits() < mandate.limitPriceMinorUnits()) {
                violations.add(new Violation(
                        Code.SELLER_PRICE_FLOOR_VIOLATED,
                        "priceMinorUnits",
                        "seller offer is below the authorized floor"));
            }
            if (terms.deliveryDays() < mandate.limitDeliveryDays()) {
                violations.add(new Violation(
                        Code.SELLER_DELIVERY_MINIMUM_VIOLATED,
                        "deliveryDays",
                        "seller delivery is below the authorized minimum"));
            }
            if (terms.includedRevisions() > mandate.limitRevisions()) {
                violations.add(new Violation(
                        Code.SELLER_REVISIONS_MAXIMUM_VIOLATED,
                        "includedRevisions",
                        "seller offer exceeds the authorized maximum revisions"));
            }
        }

        String scope = joinScope(terms);
        for (String required : safe(mandate.mustHaveDeliverables())) {
            if (!scope.contains(required.toLowerCase(Locale.ROOT))) {
                violations.add(new Violation(
                        Code.REQUIRED_DELIVERABLE_MISSING,
                        "deliverables",
                        "required deliverable is missing: " + required));
            }
        }
        for (String forbidden : safe(mandate.forbiddenTerms())) {
            if (scope.contains(forbidden.toLowerCase(Locale.ROOT))) {
                violations.add(new Violation(
                        Code.FORBIDDEN_TERM_PRESENT,
                        "terms",
                        "forbidden term is present: " + forbidden));
            }
        }
        return List.copyOf(violations);
    }

    public boolean isCompliant(
            PartyRole callerRole,
            MandateDTO mandate,
            StructuredTermsDTO terms) {
        return inspect(callerRole, mandate, terms).isEmpty();
    }

    public void requireCompliant(
            PartyRole callerRole,
            MandateDTO mandate,
            StructuredTermsDTO terms) {
        List<Violation> violations = inspect(callerRole, mandate, terms);
        if (!violations.isEmpty()) {
            throw new AiNegotiationException(
                    AiNegotiationException.Code.INVALID_TERMS,
                    "proposed terms violate the caller mandate: " + violations.getFirst().message());
        }
    }

    private static String joinScope(StructuredTermsDTO terms) {
        StringBuilder builder = new StringBuilder();
        if (terms.title() != null) {
            builder.append(terms.title()).append('\n');
        }
        for (String deliverable : safe(terms.deliverables())) {
            builder.append(deliverable).append('\n');
        }
        if (terms.acceptanceCriteriaText() != null) {
            builder.append(terms.acceptanceCriteriaText());
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }
}
