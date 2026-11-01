package com.payagent.deals;

import com.payagent.common.contracts.OfferDTO;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.mandates.MandateService;
import com.payagent.mandates.UtilityCalculator;
import com.payagent.settlement.IdempotencyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deals/{id}/offers")
public class OfferController {

    private final DealService dealService;
    private final DealRepository dealRepository;
    private final MandateService mandateService;
    private final UtilityCalculator utilityCalculator;
    private final IdempotencyService idempotencyService;
    private final OfferRepository offerRepository;

    public OfferController(DealService dealService, DealRepository dealRepository, MandateService mandateService, UtilityCalculator utilityCalculator, IdempotencyService idempotencyService, OfferRepository offerRepository) {
        this.dealService = dealService;
        this.dealRepository = dealRepository;
        this.mandateService = mandateService;
        this.utilityCalculator = utilityCalculator;
        this.idempotencyService = idempotencyService;
        this.offerRepository = offerRepository;
    }

    @PostMapping
    public ResponseEntity<OfferDTO> createOffer(
            @PathVariable("id") UUID dealId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody OfferRequest req,
            Principal principal) {
        UUID userId = resolveUserId(principal);
        PartyRole role = dealService.requireMembership(dealId, userId);
        // verify deal state
        Deal deal = dealService.getDeal(dealId);
        if (deal.getStatus() != com.payagent.common.contracts.DealStatus.ACTIVE_NEGOTIATION) {
            throw new IllegalArgumentException("Deal not in active negotiation");
        }
        String route = "/api/v1/deals/" + dealId + "/offers";
        return idempotencyService.execute(
                idempotencyKey,
                route,
                dealId,
                req,
                OfferDTO.class,
                () -> {
                    // validate mandate
                    com.payagent.common.contracts.MandateDTO mandate = mandateService.getMandateForUser(dealId, userId);
                    // basic validation
                    if (req.terms.priceMinorUnits() > mandate.limitPriceMinorUnits()) {
                        throw new IllegalArgumentException("Offer price exceeds mandate limit");
                    }
                    if (req.terms.deliveryDays() > mandate.limitDeliveryDays()) {
                        throw new IllegalArgumentException("Offer delivery exceeds mandate limit");
                    }
                    if (req.terms.includedRevisions() < mandate.limitRevisions()) {
                        throw new IllegalArgumentException("Offer revisions below mandate limit");
                    }
                    // utility veto
                    BigDecimal utility = utilityCalculator.buyerUtility(mandate, req.terms);
                    if (role == PartyRole.SELLER) {
                        utility = utilityCalculator.sellerUtility(mandate, req.terms);
                    }
                    if (utility.compareTo(BigDecimal.valueOf(-1)) == 0) {
                        throw new IllegalArgumentException("Offer violates mandate veto");
                    }
                    String utilityScore = utility.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString();
                    String rationale = req.message() == null ? "" : req.message();
                    // lock parent deal to serialize round allocation per deal
                    dealRepository.findByIdForUpdate(dealId)
                            .orElseThrow(() -> new IllegalArgumentException("Deal not found"));
                    // determine next round number
                    Integer maxRound = offerRepository.findMaxRoundNumberByDealId(dealId);
                    int nextRound = (maxRound == null) ? 1 : maxRound + 1;
                    UUID offerId = UUID.randomUUID();
                    Offer offerEntity = new Offer(
                            offerId,
                            dealId,
                            nextRound,
                            role,
                            false,
                            req.terms,
                            rationale,
                            utilityScore,
                            true
                    );
                    Offer saved = offerRepository.save(offerEntity);
                    OfferDTO offer = new OfferDTO(
                            saved.getId().toString(),
                            saved.getDealId().toString(),
                            saved.getRoundNumber(),
                            saved.getProposerRole(),
                            saved.getGeneratedByAgent(),
                            req.terms,
                            saved.getRationaleSummary(),
                            saved.getCallerUtilityScore(),
                            saved.getMandateCompliant(),
                            saved.getCreatedAt().toString()
                    );
                    return ResponseEntity.ok(offer);
                }
        );
    }

    private UUID resolveUserId(Principal principal) {
        try {
            return UUID.fromString(principal.getName());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid principal");
        }
    }

    public record OfferRequest(StructuredTermsDTO terms, String message) {}
}
