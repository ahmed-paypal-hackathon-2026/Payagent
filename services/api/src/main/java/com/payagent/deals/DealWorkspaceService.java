package com.payagent.deals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.agreements.AgreementService;
import com.payagent.auth.User;
import com.payagent.auth.UserRepository;
import com.payagent.common.contracts.AgreementSnapshotDTO;
import com.payagent.common.contracts.DealWorkspaceDTO;
import com.payagent.common.contracts.DeliverableEvidenceDTO;
import com.payagent.common.contracts.DisputeStatus;
import com.payagent.common.contracts.InvoiceLifecycleStatus;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.OfferDTO;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.PaymentLifecycleStatus;
import com.payagent.common.contracts.SettlementStateDTO;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.disputes.model.DisputeDTO;
import com.payagent.disputes.service.DisputeService;
import com.payagent.evidence.service.EvidenceStorageService;
import com.payagent.mandates.MandateService;
import com.payagent.settlement.SettlementService;
import com.payagent.settlement.exception.SettlementNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Assembles the canonical deal-room snapshot for the authenticated party.
 * Privacy: only the caller's own mandate is ever included.
 */
@Service
public class DealWorkspaceService {

    private final DealService dealService;
    private final UserRepository userRepository;
    private final MandateService mandateService;
    private final OfferRepository offerRepository;
    private final AgreementService agreementService;
    private final SettlementService settlementService;
    private final EvidenceStorageService evidenceStorageService;
    private final DisputeService disputeService;
    private final ObjectMapper objectMapper;

    public DealWorkspaceService(DealService dealService,
                                UserRepository userRepository,
                                MandateService mandateService,
                                OfferRepository offerRepository,
                                AgreementService agreementService,
                                SettlementService settlementService,
                                EvidenceStorageService evidenceStorageService,
                                DisputeService disputeService,
                                ObjectMapper objectMapper) {
        this.dealService = dealService;
        this.userRepository = userRepository;
        this.mandateService = mandateService;
        this.offerRepository = offerRepository;
        this.agreementService = agreementService;
        this.settlementService = settlementService;
        this.evidenceStorageService = evidenceStorageService;
        this.disputeService = disputeService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DealWorkspaceDTO getWorkspace(UUID dealId, UUID userId) {
        PartyRole myRole = dealService.requireMembership(dealId, userId);
        Deal deal = dealService.getDeal(dealId);
        User caller = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Caller user not found"));

        MandateDTO myMandate = null;
        try {
            myMandate = mandateService.getMandateForUser(dealId, userId);
        } catch (IllegalArgumentException ex) {
            myMandate = null;
        }

        List<OfferDTO> offers = offerRepository.findByDealIdOrderByRoundNumberAsc(dealId).stream()
                .map(this::toOfferDto)
                .toList();

        AgreementSnapshotDTO currentAgreement = agreementService.getLatestSnapshot(dealId).orElse(null);

        SettlementStateDTO settlement = buildSettlementState(dealId, caller.getEmail(), currentAgreement);

        List<DeliverableEvidenceDTO> evidenceList = evidenceStorageService.listEvidenceForDeal(dealId, caller.getEmail());

        DisputeStatus disputeStatus = DisputeStatus.NONE;
        String disputeReason = null;
        List<DisputeDTO> disputes = disputeService.getDisputesForDeal(dealId, caller.getEmail());
        if (!disputes.isEmpty()) {
            DisputeDTO latest = disputes.get(0);
            disputeStatus = latest.status();
            disputeReason = latest.reason();
        }

        String buyerEmail = userRepository.findById(deal.getBuyerUserId()).map(User::getEmail).orElse(null);
        String sellerEmail = deal.getSellerUserId() == null
                ? null
                : userRepository.findById(deal.getSellerUserId()).map(User::getEmail).orElse(null);

        return new DealWorkspaceDTO(
                dealId.toString(),
                deal.getStatus(),
                deal.getStopReason(),
                buyerEmail,
                sellerEmail,
                myRole,
                myMandate,
                offers,
                currentAgreement,
                settlement,
                evidenceList,
                disputeStatus,
                disputeReason
        );
    }

    private SettlementStateDTO buildSettlementState(UUID dealId, String callerEmail, AgreementSnapshotDTO currentAgreement) {
        try {
            return settlementService.getSettlementState(dealId, callerEmail);
        } catch (SettlementNotFoundException ex) {
            int version = currentAgreement != null ? currentAgreement.version() : 1;
            String currency = currentAgreement != null ? currentAgreement.canonicalTerms().currency() : "USD";
            return new SettlementStateDTO(
                    dealId.toString(),
                    version,
                    InvoiceLifecycleStatus.NOT_CREATED,
                    null,
                    null,
                    null,
                    PaymentLifecycleStatus.NOT_STARTED,
                    null,
                    null,
                    null,
                    null,
                    0L,
                    currency,
                    Instant.now().toString()
            );
        }
    }

    private OfferDTO toOfferDto(Offer offer) {
        StructuredTermsDTO terms;
        try {
            terms = objectMapper.readValue(offer.getTermsJson(), StructuredTermsDTO.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize offer terms", e);
        }
        return new OfferDTO(
                offer.getId().toString(),
                offer.getDealId().toString(),
                offer.getRoundNumber(),
                offer.getProposerRole(),
                offer.getGeneratedByAgent(),
                terms,
                offer.getRationaleSummary(),
                offer.getCallerUtilityScore(),
                offer.getMandateCompliant(),
                offer.getCreatedAt().toString()
        );
    }
}
