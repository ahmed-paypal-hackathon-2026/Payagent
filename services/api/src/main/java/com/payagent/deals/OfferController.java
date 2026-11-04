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

// @org.springframework.stereotype.Component("dealOfferController") disabled to avoid bean creation
// @RestController disabled to avoid duplicate mapping with mandates OfferController
// @RequestMapping("/api/v1/deals/{id}/offers")
public class OfferController {

    private final DealService dealService;
    private final DealRepository dealRepository;
    private final MandateService mandateService;
    private final UtilityCalculator utilityCalculator;
    private final IdempotencyService idempotencyService;
    // private final OfferRepository offerRepository; disabled

    public OfferController(DealService dealService, DealRepository dealRepository, MandateService mandateService, UtilityCalculator utilityCalculator, IdempotencyService idempotencyService/*, OfferRepository offerRepository*/) {
        this.dealService = dealService;
        this.dealRepository = dealRepository;
        this.mandateService = mandateService;
        this.utilityCalculator = utilityCalculator;
        this.idempotencyService = idempotencyService;
        // this.offerRepository = offerRepository;
    }

    // Disabled duplicate offer controller methods
    // @PostMapping
    // public ResponseEntity<OfferDTO> createOffer(...) { ... }

    private UUID resolveUserId(Principal principal) {
        try {
            return UUID.fromString(principal.getName());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid principal");
        }
    }

    public record OfferRequest(StructuredTermsDTO terms, String message) {}
}
