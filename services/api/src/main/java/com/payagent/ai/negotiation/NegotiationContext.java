package com.payagent.ai.negotiation;

import com.payagent.ai.AiNegotiationException;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.OfferDTO;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Versioned, read-only snapshot of the information available at a decision
 * point. Member A constructs this from persisted deal/offer state; Member C
 * never mutates the database.
 *
 * @param stateVersion representation version; currently {@value #CURRENT_VERSION}
 * @param dealId       deal identifier
 * @param callerRole   the party on whose behalf the agent acts
 * @param mandate      the caller's own mandate (never the counterparty's)
 * @param baseTerms    the caller's intended scope, used when no counterparty offer exists
 * @param offers       offer history, oldest first (order is normalized on construction)
 * @param preferences  caller preferences
 */
public record NegotiationContext(
        int stateVersion,
        String dealId,
        PartyRole callerRole,
        MandateDTO mandate,
        StructuredTermsDTO baseTerms,
        List<OfferDTO> offers,
        NegotiationPreferences preferences) {

    public static final int CURRENT_VERSION = 1;

    public NegotiationContext {
        if (stateVersion != CURRENT_VERSION) {
            throw new AiNegotiationException(
                    AiNegotiationException.Code.INVALID_STATE,
                    "unsupported negotiation state version: " + stateVersion);
        }
        if (dealId == null || dealId.isBlank()) {
            throw new AiNegotiationException(
                    AiNegotiationException.Code.INVALID_STATE, "dealId is required");
        }
        if (callerRole == null) {
            throw new AiNegotiationException(
                    AiNegotiationException.Code.INVALID_STATE, "callerRole is required");
        }
        if (mandate == null) {
            throw new AiNegotiationException(
                    AiNegotiationException.Code.INVALID_STATE, "mandate is required");
        }
        if (baseTerms == null) {
            throw new AiNegotiationException(
                    AiNegotiationException.Code.INVALID_STATE, "baseTerms are required");
        }
        if (preferences == null) {
            throw new AiNegotiationException(
                    AiNegotiationException.Code.INVALID_STATE, "preferences are required");
        }
        List<OfferDTO> sorted = new ArrayList<>(offers == null ? List.of() : offers);
        sorted.sort(Comparator.comparingInt(OfferDTO::roundNumber)
                .thenComparing(OfferDTO::createdAt, Comparator.nullsFirst(Comparator.naturalOrder())));
        offers = List.copyOf(sorted);
    }

    public static NegotiationContext of(
            String dealId,
            PartyRole callerRole,
            MandateDTO mandate,
            StructuredTermsDTO baseTerms,
            List<OfferDTO> offers,
            NegotiationPreferences preferences) {
        return new NegotiationContext(
                CURRENT_VERSION, dealId, callerRole, mandate, baseTerms, offers, preferences);
    }

    public String currency() {
        return mandate.currency();
    }

    public int nextRoundNumber() {
        return offers.stream().mapToInt(OfferDTO::roundNumber).max().orElse(0) + 1;
    }

    public List<OfferDTO> ownOffers() {
        return offers.stream().filter(o -> o.proposerRole() == callerRole).toList();
    }

    public List<OfferDTO> counterpartyOffers() {
        return offers.stream().filter(o -> o.proposerRole() != callerRole).toList();
    }

    public OfferDTO latestCounterpartyOffer() {
        OfferDTO latest = null;
        for (OfferDTO offer : offers) {
            if (offer.proposerRole() != callerRole) {
                latest = offer;
            }
        }
        return latest;
    }

    public OfferDTO latestOwnOffer() {
        OfferDTO latest = null;
        for (OfferDTO offer : offers) {
            if (offer.proposerRole() == callerRole) {
                latest = offer;
            }
        }
        return latest;
    }
}
