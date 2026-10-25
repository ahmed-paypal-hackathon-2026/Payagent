package com.payagent.ai;

import com.payagent.ai.negotiation.NegotiationContext;
import com.payagent.ai.negotiation.NegotiationPreferences;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.OfferDTO;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;

import java.time.Instant;
import java.util.List;

public final class AiTestFixtures {

    public static final String DEAL_ID = "deal-1";
    public static final Instant NOW = Instant.parse("2029-12-31T00:00:00Z");
    public static final String FUTURE = "2030-01-01T00:00:00Z";

    private AiTestFixtures() {}

    public static MandateDTO buyerMandate() {
        return new MandateDTO(
                "mandate-buyer",
                DEAL_ID,
                PartyRole.BUYER,
                1,
                "USD",
                100_000L,
                80_000L,
                30,
                21,
                2,
                2,
                List.of("source code"),
                List.of("crypto payment"),
                NegotiationMode.AUTONOMOUS_BOUNDED,
                6,
                FUTURE);
    }

    public static MandateDTO sellerMandate() {
        return new MandateDTO(
                "mandate-seller",
                DEAL_ID,
                PartyRole.SELLER,
                1,
                "USD",
                70_000L,
                90_000L,
                14,
                21,
                2,
                2,
                List.of("source code"),
                List.of("crypto payment"),
                NegotiationMode.AUTONOMOUS_BOUNDED,
                6,
                FUTURE);
    }

    public static MandateDTO withMode(MandateDTO mandate, NegotiationMode mode) {
        return new MandateDTO(
                mandate.mandateId(),
                mandate.dealId(),
                mandate.partyRole(),
                mandate.version(),
                mandate.currency(),
                mandate.limitPriceMinorUnits(),
                mandate.targetPriceMinorUnits(),
                mandate.limitDeliveryDays(),
                mandate.targetDeliveryDays(),
                mandate.limitRevisions(),
                mandate.targetRevisions(),
                mandate.mustHaveDeliverables(),
                mandate.forbiddenTerms(),
                mode,
                mandate.maxRounds(),
                mandate.expiresAt());
    }

    public static MandateDTO withExpiry(MandateDTO mandate, String expiry) {
        return new MandateDTO(
                mandate.mandateId(),
                mandate.dealId(),
                mandate.partyRole(),
                mandate.version(),
                mandate.currency(),
                mandate.limitPriceMinorUnits(),
                mandate.targetPriceMinorUnits(),
                mandate.limitDeliveryDays(),
                mandate.targetDeliveryDays(),
                mandate.limitRevisions(),
                mandate.targetRevisions(),
                mandate.mustHaveDeliverables(),
                mandate.forbiddenTerms(),
                mandate.mode(),
                mandate.maxRounds(),
                expiry);
    }

    public static StructuredTermsDTO terms(long price) {
        return terms(price, "USD");
    }

    public static StructuredTermsDTO terms(long price, String currency) {
        return new StructuredTermsDTO(
                "Website build",
                List.of("source code", "documentation"),
                price,
                currency,
                21,
                2,
                "All source code and documentation are delivered and the site builds.");
    }

    public static StructuredTermsDTO terms(
            long price,
            int deliveryDays,
            int revisions,
            String title,
            List<String> deliverables) {
        return new StructuredTermsDTO(
                title,
                deliverables,
                price,
                "USD",
                deliveryDays,
                revisions,
                "The buyer confirms that the listed deliverables are complete.");
    }

    public static OfferDTO offer(int round, PartyRole role, long price) {
        return offer(DEAL_ID, round, role, price, terms(price));
    }

    public static OfferDTO offer(
            String dealId,
            int round,
            PartyRole role,
            long price,
            StructuredTermsDTO terms) {
        return new OfferDTO(
                "offer-" + round,
                dealId,
                round,
                role,
                false,
                terms,
                "Test offer.",
                "0.5",
                true,
                NOW.plusSeconds(round * 60L).toString());
    }

    public static NegotiationContext context(
            PartyRole role,
            MandateDTO mandate,
            List<OfferDTO> offers) {
        return context(role, mandate, offers, NegotiationPreferences.DEFAULT);
    }

    public static NegotiationContext context(
            PartyRole role,
            MandateDTO mandate,
            List<OfferDTO> offers,
            NegotiationPreferences preferences) {
        return NegotiationContext.of(
                DEAL_ID,
                role,
                mandate,
                terms(mandate.targetPriceMinorUnits()),
                offers,
                preferences);
    }
}
