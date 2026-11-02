package com.payagent.deals;

import com.payagent.agreements.AgreementService;
import com.payagent.ai.negotiation.NegotiationAction;
import com.payagent.ai.negotiation.ProposedOfferDTO;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.mandates.MandateService;
import com.payagent.mandates.UtilityCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class NegotiationAgreementBridge {

    private final DealService dealService;
    private final AgreementService agreementService;
    private final MandateService mandateService;
    private final UtilityCalculator utilityCalculator;

    public NegotiationAgreementBridge(DealService dealService, AgreementService agreementService,
                                      MandateService mandateService, UtilityCalculator utilityCalculator) {
        this.dealService = dealService;
        this.agreementService = agreementService;
        this.mandateService = mandateService;
        this.utilityCalculator = utilityCalculator;
    }

    @Transactional
    public com.payagent.common.contracts.AgreementSnapshotDTO proposeFromNegotiation(UUID dealId, UUID userId, ProposedOfferDTO decision) {
        // Membership check
        com.payagent.common.contracts.PartyRole role = dealService.requireMembership(dealId, userId);
        // Status check
        Deal deal = dealService.getDeal(dealId);
        if (deal.getStatus() != DealStatus.ACTIVE_NEGOTIATION) {
            throw new IllegalStateException("Deal not in active negotiation");
        }
        // Trust boundary: re-validate terms
        StructuredTermsDTO terms = decision.terms();
        if (terms == null) {
            throw new IllegalArgumentException("Terms required");
        }
        // Re-validate mandate server-side and run the trader-side utility veto
        com.payagent.common.contracts.MandateDTO mandate = mandateService.getMandateForUser(dealId, userId);
        BigDecimal utility = role == com.payagent.common.contracts.PartyRole.BUYER
                ? utilityCalculator.buyerUtility(mandate, terms)
                : utilityCalculator.sellerUtility(mandate, terms);
        if (utility.compareTo(BigDecimal.valueOf(-1)) == 0) {
            throw new IllegalArgumentException("Terms violate caller mandate veto");
        }
        if (decision.action() == NegotiationAction.ACCEPT && !decision.requiresHumanApproval()) {
            return agreementService.proposeAgreement(dealId, terms);
        }
        throw new IllegalStateException("Negotiation decision not eligible for agreement proposal");
    }
}
