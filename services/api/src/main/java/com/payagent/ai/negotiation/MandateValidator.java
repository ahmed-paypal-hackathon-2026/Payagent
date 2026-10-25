package com.payagent.ai.negotiation;

import org.springframework.stereotype.Component;
import com.payagent.ai.AiNegotiationException;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validates a user's mandate before any negotiation decision is made.
 *
 * <p>Role semantics follow the canonical contract and plan section 2:</p>
 * <ul>
 *   <li><b>BUYER</b> — {@code limitPriceMinorUnits} is a ceiling, the target must
 *       be at or below it; {@code limitDeliveryDays} is a maximum and
 *       {@code limitRevisions} is a minimum.</li>
 *   <li><b>SELLER</b> — {@code limitPriceMinorUnits} is a floor, the target must
 *       be at or above it; {@code limitDeliveryDays} is a minimum and
 *       {@code limitRevisions} is a maximum.</li>
 * </ul>
 */
@Component
public final class MandateValidator {

    public static final int MAX_ROUNDS_LIMIT = 50;

    public void validate(MandateDTO mandate) {
        validate(mandate, Instant.now());
    }

    public void validate(MandateDTO mandate, Instant now) {
        if (mandate == null) {
            throw invalid("mandate is missing");
        }
        requireText("mandateId", mandate.mandateId());
        requireText("dealId", mandate.dealId());
        requireCurrency(mandate.currency());
        requireRole(mandate.partyRole());
        requireMode(mandate.mode());
        requireExpiry(mandate.expiresAt(), now);

        if (mandate.version() < 1) {
            throw invalid("mandate version must be at least 1");
        }
        if (mandate.maxRounds() < 1 || mandate.maxRounds() > MAX_ROUNDS_LIMIT) {
            throw invalid("maxRounds must be between 1 and " + MAX_ROUNDS_LIMIT);
        }
        if (mandate.limitPriceMinorUnits() < 0 || mandate.targetPriceMinorUnits() < 0) {
            throw invalid("price limits must not be negative");
        }
        if (mandate.limitDeliveryDays() < 1 || mandate.targetDeliveryDays() < 1) {
            throw invalid("delivery days must be at least 1");
        }
        if (mandate.limitRevisions() < 0 || mandate.targetRevisions() < 0) {
            throw invalid("revision limits must not be negative");
        }
        validateLists(mandate.mustHaveDeliverables(), "mustHaveDeliverables");
        validateLists(mandate.forbiddenTerms(), "forbiddenTerms");

        if (mandate.partyRole() == PartyRole.BUYER) {
            if (mandate.targetPriceMinorUnits() > mandate.limitPriceMinorUnits()) {
                throw invalid("buyer target price must not exceed the buyer limit price");
            }
            if (mandate.targetDeliveryDays() > mandate.limitDeliveryDays()) {
                throw invalid("buyer target delivery days must not exceed the buyer limit delivery days");
            }
            if (mandate.targetRevisions() < mandate.limitRevisions()) {
                throw invalid("buyer target revisions must not be below the buyer minimum revisions");
            }
        } else {
            if (mandate.targetPriceMinorUnits() < mandate.limitPriceMinorUnits()) {
                throw invalid("seller target price must not be below the seller floor price");
            }
            if (mandate.targetDeliveryDays() < mandate.limitDeliveryDays()) {
                throw invalid("seller target delivery days must not be below the seller minimum delivery days");
            }
            if (mandate.targetRevisions() > mandate.limitRevisions()) {
                throw invalid("seller target revisions must not exceed the seller maximum revisions");
            }
        }
    }

    private static void validateLists(List<String> values, String field) {
        if (values == null) {
            throw invalid(field + " must not be null");
        }
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw invalid(field + " must not contain blank entries");
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (!seen.add(normalized)) {
                throw invalid(field + " contains duplicate entries");
            }
        }
    }

    private static void requireExpiry(String expiresAt, Instant now) {
        if (expiresAt == null || expiresAt.isBlank()) {
            throw invalid("expiresAt is missing");
        }
        Instant expiry;
        try {
            expiry = Instant.parse(expiresAt);
        } catch (DateTimeParseException ex) {
            throw invalid("expiresAt must be an ISO-8601 instant");
        }
        if (!expiry.isAfter(now)) {
            throw new AiNegotiationException(
                    AiNegotiationException.Code.MANDATE_EXPIRED,
                    "mandate has expired");
        }
    }

    private static void requireCurrency(String currency) {
        if (currency == null || currency.length() != 3 || !currency.chars().allMatch(Character::isLetter)) {
            throw invalid("currency must be a three-letter ISO-4217 code");
        }
    }

    private static void requireRole(PartyRole role) {
        if (role == null) {
            throw invalid("partyRole is missing");
        }
    }

    private static void requireMode(NegotiationMode mode) {
        if (mode == null) {
            throw invalid("negotiation mode is missing");
        }
    }

    private static void requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " is missing");
        }
    }

    private static AiNegotiationException invalid(String message) {
        return new AiNegotiationException(AiNegotiationException.Code.INVALID_MANDATE, message);
    }
}
