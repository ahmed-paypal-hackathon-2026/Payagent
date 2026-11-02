package com.payagent.ai.evaluation;

import com.payagent.ai.negotiation.NegotiationContext;
import com.payagent.ai.negotiation.NegotiationPreferences;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.OfferDTO;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;

import java.util.List;

/**
 * One reproducible synthetic negotiation scenario.
 *
 * <p>The caller's mandate, preferences and base scope are visible to the agent.
 * The counterparty reservation/behaviour fields are hidden from the agent and
 * are used only by the simulator to generate responses and to score an outcome.
 * This is the same information asymmetry that exists in the product.</p>
 */
public record SyntheticScenario(
        String scenarioId,
        PartyRole callerRole,
        NegotiationPreferences preferences,
        MandateDTO mandate,
        StructuredTermsDTO baseTerms,
        boolean counterpartyOpensFirst,
        long counterpartyFloor,
        long counterpartyTarget,
        long counterpartyOpening,
        long counterpartyCeiling,
        double counterpartyAcceptanceShare,
        double counterpartyConcessionRate) {

    public SyntheticScenario {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new IllegalArgumentException("scenarioId is required");
        }
        if (callerRole == null || preferences == null || mandate == null || baseTerms == null) {
            throw new IllegalArgumentException("scenario fields are required");
        }
        if (mandate.partyRole() != callerRole) {
            throw new IllegalArgumentException("mandate role must match the caller role");
        }
        if (!baseTerms.currency().equalsIgnoreCase(mandate.currency())) {
            throw new IllegalArgumentException("base terms currency must match the mandate");
        }
    }

    public PartyRole counterpartyRole() {
        return callerRole == PartyRole.BUYER ? PartyRole.SELLER : PartyRole.BUYER;
    }

    public int maxRounds() {
        return mandate.maxRounds();
    }

    public NegotiationContext context(List<OfferDTO> offers) {
        return NegotiationContext.of(
                mandate.dealId(), callerRole, mandate, baseTerms, offers, preferences);
    }
}
