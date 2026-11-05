package com.payagent.deals;

import com.payagent.common.contracts.OfferDTO;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OfferRecorder {

    private final DealRepository dealRepository;
    private final OfferRepository offerRepository;

    public OfferRecorder(DealRepository dealRepository, OfferRepository offerRepository) {
        this.dealRepository = dealRepository;
        this.offerRepository = offerRepository;
    }

    @Transactional
    public OfferDTO recordOffer(UUID dealId, PartyRole role, StructuredTermsDTO terms, String rationale, String utilityScore) {
        // lock parent deal to serialize round allocation per deal
        dealRepository.findByIdForUpdate(dealId)
                .orElseThrow(() -> new IllegalArgumentException("Deal not found"));
        int nextRound;
        Integer maxRound = offerRepository.findMaxRoundNumberByDealId(dealId);
        nextRound = (maxRound == null) ? 1 : maxRound + 1;
        UUID offerId = UUID.randomUUID();
        Offer offerEntity = new Offer(
                offerId,
                dealId,
                nextRound,
                role,
                false,
                terms,
                rationale,
                utilityScore,
                true
        );
        Offer saved = offerRepository.save(offerEntity);
        return new OfferDTO(
                saved.getId().toString(),
                saved.getDealId().toString(),
                saved.getRoundNumber(),
                saved.getProposerRole(),
                saved.getGeneratedByAgent(),
                terms,
                saved.getRationaleSummary(),
                saved.getCallerUtilityScore(),
                saved.getMandateCompliant(),
                saved.getCreatedAt().toString()
        );
    }
}
