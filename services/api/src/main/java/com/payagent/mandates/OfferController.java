package com.payagent.mandates;

import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.OfferDTO;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.deals.DealService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@org.springframework.stereotype.Component("mandateOfferController")
@RestController
@RequestMapping("/api/v1/deals/{dealId}/offers")
public class OfferController {

    private final MandateService mandateService;
    private final DealService dealService;
    private final OfferRepository offerRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper mapper = new ObjectMapper();

    public OfferController(MandateService mandateService, DealService dealService,
                           OfferRepository offerRepository,
                           @Qualifier("mandateIdempotencyService") IdempotencyService idempotencyService) {
        this.mandateService = mandateService;
        this.dealService = dealService;
        this.offerRepository = offerRepository;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Creates a new offer on a deal. The offered terms are validated against the
     * caller's active mandate; offers that violate the mandate are rejected with
     * HTTP 422. The caller's utility score is computed deterministically and
     * recorded on the offer.
     *
     * <p>Requires {@code Idempotency-Key} header. Same key + same request replays
     * the cached response; same key + different request returns HTTP 409.
     */
    @PostMapping
    public ResponseEntity<OfferDTO> createOffer(
            HttpServletRequest request,
            @PathVariable UUID dealId,
            @Valid @RequestBody CreateOfferRequest requestBody,
            @RequestAttribute(value = "userId", required = false) UUID userId) {

        String idempotencyKey = request.getHeader("Idempotency-Key");

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        PartyRole role;
        try {
            role = dealService.requireMembership(dealId, userId);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Get the caller's active (latest) mandate for this deal.
        MandateDTO mandate;
        try {
            mandate = mandateService.getMandateForUser(dealId, userId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        StructuredTermsDTO terms = requestBody.terms();

        String route = "POST /api/v1/deals/" + dealId + "/offers";
        byte[] requestBytes;
        try {
            requestBytes = mapper.writeValueAsBytes(requestBody);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        IdempotencyService.IdempotentResponse result =
                idempotencyService.execute(idempotencyKey, route, dealId, requestBytes,
                        () -> {
                            // Validate terms against the caller's mandate.
                            boolean mandateCompliant =
                                    UtilityCalculator.isMandateCompliant(role, mandate, terms);
                            if (!mandateCompliant) {
                                return new IdempotencyService.IdempotentResponse(
                                        HttpStatus.UNPROCESSABLE_ENTITY.value(), null, false);
                            }

                            // Compute the caller's utility score deterministically.
                            BigDecimal utility = UtilityCalculator.utilityFor(role, mandate, terms);
                            String callerUtilityScore = UtilityCalculator.formatScore(utility);

                            // Determine the next round number.
                            Integer nextRound = offerRepository.findMaxRoundNumberByDealId(dealId);
                            nextRound = (nextRound == null) ? 1 : nextRound + 1;

                            Offer offer = new Offer(
                                    UUID.randomUUID(), dealId, nextRound, role, false,
                                    terms, requestBody.message(), callerUtilityScore, mandateCompliant
                            );
                            Offer saved = offerRepository.save(offer);

                            OfferDTO dto = new OfferDTO(
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
                            try {
                                return new IdempotencyService.IdempotentResponse(
                                        HttpStatus.CREATED.value(),
                                        mapper.writeValueAsString(dto),
                                        false);
                            } catch (Exception e) {
                                return new IdempotencyService.IdempotentResponse(
                                        HttpStatus.INTERNAL_SERVER_ERROR.value(), null, false);
                            }
                        });

        if (result.status() == 409) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        if (result.replayed() && result.body() != null) {
            try {
                OfferDTO replayed = mapper.readValue(result.body(), OfferDTO.class);
                return ResponseEntity.status(HttpStatus.CREATED).body(replayed);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }
        if (result.body() != null) {
            try {
                OfferDTO created = mapper.readValue(result.body(), OfferDTO.class);
                return ResponseEntity.status(HttpStatus.CREATED).body(created);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }
        return ResponseEntity.status(HttpStatus.valueOf(result.status())).build();
    }

    /**
     * Request body for {@link #createOffer}. The {@code terms} field contains the
     * proposed structured terms; {@code message} is a free-text rationale.
     */
    public record CreateOfferRequest(StructuredTermsDTO terms, String message) {}
}
