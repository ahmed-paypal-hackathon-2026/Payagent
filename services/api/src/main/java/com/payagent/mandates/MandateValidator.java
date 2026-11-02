package com.payagent.mandates;

import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Domain-level mandate validation for mandate creation, independent of the
 * AI package validator. Enforces plan Section 2 role semantics:
 * BUYER limit is a ceiling, SELLER limit is a floor; delivery days follow the
 * same direction; revisions are inverted (BUYER minimum, SELLER maximum).
 */
@Component("domainMandateValidator")
public class MandateValidator {

    public static final int MAX_ROUNDS_LIMIT = 50;

    public void validate(MandateService.MandateCreateRequest req, PartyRole role) {
        if (req == null) {
            throw invalid("Mandate request is required");
        }
        if (role == null) {
            throw invalid("partyRole is required");
        }
        requireCurrency(req.currency());
        requireMode(req.mode());
        requirePrices(req, role);
        requireDeliveryDays(req, role);
        requireRevisions(req, role);
        requireRounds(req.maxRounds());
        requireExpiry(req.expiresAt());
        requireList(req.mustHaveDeliverables(), "mustHaveDeliverables");
        requireList(req.forbiddenTerms(), "forbiddenTerms");
    }

    private void requireCurrency(String currency) {
        if (currency == null || currency.length() != 3 || !currency.chars().allMatch(Character::isLetter)) {
            throw invalid("Currency must be a three-letter ISO-4217 code");
        }
    }

    private void requireMode(NegotiationMode mode) {
        if (mode == null) {
            throw invalid("Negotiation mode is required");
        }
    }

    private void requirePrices(MandateService.MandateCreateRequest req, PartyRole role) {
        if (req.limitPriceMinorUnits() <= 0 || req.targetPriceMinorUnits() <= 0) {
            throw invalid("Price must be positive");
        }
        if (role == PartyRole.BUYER && req.targetPriceMinorUnits() > req.limitPriceMinorUnits()) {
            throw invalid("Target price cannot exceed limit for BUYER");
        }
        if (role == PartyRole.SELLER && req.targetPriceMinorUnits() < req.limitPriceMinorUnits()) {
            throw invalid("Target price cannot be below limit for SELLER");
        }
    }

    private void requireDeliveryDays(MandateService.MandateCreateRequest req, PartyRole role) {
        if (req.limitDeliveryDays() < 1 || req.targetDeliveryDays() < 1) {
            throw invalid("Delivery days must be at least 1");
        }
        if (role == PartyRole.BUYER && req.targetDeliveryDays() > req.limitDeliveryDays()) {
            throw invalid("Target delivery days cannot exceed limit for BUYER");
        }
        if (role == PartyRole.SELLER && req.targetDeliveryDays() < req.limitDeliveryDays()) {
            throw invalid("Target delivery days cannot be below limit for SELLER");
        }
    }

    private void requireRevisions(MandateService.MandateCreateRequest req, PartyRole role) {
        if (req.limitRevisions() < 0 || req.targetRevisions() < 0) {
            throw invalid("Revisions must not be negative");
        }
        if (role == PartyRole.BUYER && req.targetRevisions() < req.limitRevisions()) {
            throw invalid("Target revisions cannot be below limit for BUYER");
        }
        if (role == PartyRole.SELLER && req.targetRevisions() > req.limitRevisions()) {
            throw invalid("Target revisions cannot exceed limit for SELLER");
        }
    }

    private void requireRounds(int maxRounds) {
        if (maxRounds < 1 || maxRounds > MAX_ROUNDS_LIMIT) {
            throw invalid("maxRounds must be between 1 and " + MAX_ROUNDS_LIMIT);
        }
    }

    private void requireExpiry(OffsetDateTime expiresAt) {
        if (expiresAt == null) {
            throw invalid("expiresAt is required");
        }
        if (!expiresAt.isAfter(OffsetDateTime.now())) {
            throw invalid("expiresAt must be in the future");
        }
    }

    private void requireList(List<String> values, String field) {
        if (values == null) {
            throw invalid(field + " must not be null");
        }
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw invalid(field + " must not contain blank entries");
            }
            if (!seen.add(value.trim().toLowerCase(Locale.ROOT))) {
                throw invalid(field + " contains duplicate entries");
            }
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
