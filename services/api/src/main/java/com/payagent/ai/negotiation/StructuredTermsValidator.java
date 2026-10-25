package com.payagent.ai.negotiation;

import com.payagent.ai.AiNegotiationException;
import com.payagent.common.contracts.StructuredTermsDTO;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Schema-level validation for intake/counteroffer terms, independent of a mandate. */
@Component
public class StructuredTermsValidator {

    public void validate(StructuredTermsDTO terms) {
        if (terms == null) {
            throw invalid("terms are missing");
        }
        if (terms.title() == null || terms.title().isBlank()
                || terms.title().length() > 200) {
            throw invalid("title must be non-blank and at most 200 characters");
        }
        if (terms.deliverables() == null || terms.deliverables().isEmpty()
                || terms.deliverables().size() > 50) {
            throw invalid("deliverables must contain between 1 and 50 entries");
        }
        Set<String> seen = new HashSet<>();
        for (String deliverable : terms.deliverables()) {
            if (deliverable == null || deliverable.isBlank() || deliverable.length() > 300) {
                throw invalid("each deliverable must be non-blank and at most 300 characters");
            }
            if (!seen.add(deliverable.trim().toLowerCase(Locale.ROOT))) {
                throw invalid("deliverables must not contain duplicates");
            }
        }
        if (terms.priceMinorUnits() < 0) {
            throw invalid("price must not be negative");
        }
        if (terms.currency() == null || terms.currency().length() != 3
                || !terms.currency().chars().allMatch(Character::isLetter)) {
            throw invalid("currency must be a three-letter ISO-4217 code");
        }
        if (terms.deliveryDays() < 1 || terms.deliveryDays() > 3650) {
            throw invalid("deliveryDays must be between 1 and 3650");
        }
        if (terms.includedRevisions() < 0 || terms.includedRevisions() > 100) {
            throw invalid("includedRevisions must be between 0 and 100");
        }
        if (terms.acceptanceCriteriaText() == null || terms.acceptanceCriteriaText().isBlank()
                || terms.acceptanceCriteriaText().length() > 2000) {
            throw invalid("acceptanceCriteriaText must be non-blank and at most 2000 characters");
        }
    }

    public boolean isValid(StructuredTermsDTO terms) {
        try {
            validate(terms);
            return true;
        } catch (AiNegotiationException ex) {
            return false;
        }
    }

    public List<String> issues(StructuredTermsDTO terms) {
        try {
            validate(terms);
            return List.of();
        } catch (AiNegotiationException ex) {
            return List.of(ex.getMessage());
        }
    }

    private static AiNegotiationException invalid(String message) {
        return new AiNegotiationException(AiNegotiationException.Code.INVALID_TERMS, message);
    }
}
