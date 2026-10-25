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
    private final MandateService mandateService;
    private final UtilityCalculator utilityCalculator;
    private final IdempotencyService idempotencyService;

    public OfferController(DealService dealService, MandateService mandateService, UtilityCalculator utilityCalculator, IdempotencyService idempotencyService) {
        this.dealService = dealService;
        this.mandateService = mandateService;
        this.utilityCalculator = utilityCalculator;
        this.idempotencyService = idempotencyService;
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
                    // for seller use seller utility if role is seller
                    if (role == PartyRole.SELLER) {
                        utility = utilityCalculator.sellerUtility(mandate, req.terms);
                    }
                    if (utility.compareTo(BigDecimal.valueOf(-1)) == 0) {
                        throw new IllegalArgumentException("Offer violates mandate veto");
                    }
                    String utilityScore = utility.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString();
                    OfferDTO offer = new OfferDTO(
                            UUID.randomUUID().toString(),
                            dealId.toString(),
                            1,
                            role,
                            false,
                            req.terms,
                            req.message() == null ? "" : req.message(),
                            utilityScore,
                            true,
                            OffsetDateTime.now().toString()
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
