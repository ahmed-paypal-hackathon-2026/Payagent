package com.payagent.ai.negotiation;

import com.payagent.ai.strategy.StrategySelection;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;

import java.util.List;

/**
 * AI-owned decision envelope. It is deliberately not a canonical contract: the
 * application (Member A) maps this to an {@code OfferDTO} only after applying
 * its own state-machine and authorization checks.
 */
public record ProposedOfferDTO(
        String dealId,
        PartyRole proposerRole,
        NegotiationAction action,
        NegotiationStrategy strategy,
        StrategySelection.Source strategySource,
        StructuredTermsDTO terms,
        long proposedPriceMinorUnits,
        String rationaleSummary,
        double confidence,
        boolean generatedByAgent,
        boolean fallbackUsed,
        boolean requiresHumanApproval,
        List<String> warnings,
        String reasonCode) {

    public ProposedOfferDTO {
        if (action == null || strategy == null || strategySource == null) {
            throw new IllegalArgumentException("action and strategy are required");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        if ((action == NegotiationAction.OPEN
                || action == NegotiationAction.COUNTER
                || action == NegotiationAction.ACCEPT) && terms == null) {
            throw new IllegalArgumentException("terms are required for " + action);
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        if (terms != null && proposedPriceMinorUnits != terms.priceMinorUnits()) {
            throw new IllegalArgumentException("proposed price must match terms");
        }
        if (rationaleSummary == null) {
            rationaleSummary = "";
        }
    }

    public boolean hasTerms() {
        return terms != null;
    }
}
