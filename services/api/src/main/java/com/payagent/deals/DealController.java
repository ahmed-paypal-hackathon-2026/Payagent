package com.payagent.deals;

import com.payagent.ai.intake.IntakeExtractionService;
import com.payagent.ai.negotiation.StructuredTermsValidator;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.settlement.IdempotencyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deals")
public class DealController {

    private final DealService dealService;
    private final IdempotencyService idempotencyService;
    private final IntakeExtractionService intakeExtractionService;
    private final StructuredTermsValidator termsValidator;
    private final DealRepository dealRepository;

    public DealController(DealService dealService, IdempotencyService idempotencyService, IntakeExtractionService intakeExtractionService, StructuredTermsValidator termsValidator, DealRepository dealRepository) {
        this.dealService = dealService;
        this.idempotencyService = idempotencyService;
        this.intakeExtractionService = intakeExtractionService;
        this.termsValidator = termsValidator;
        this.dealRepository = dealRepository;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createDeal(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Principal principal) {
        UUID userId = resolveUserId(principal);
        String route = "/api/v1/deals";
        String payload = userId.toString();
        @SuppressWarnings("unchecked")
        Class<Map<String,String>> cls = (Class<Map<String,String>>)(Class<?>)Map.class;
        return idempotencyService.execute(
                idempotencyKey,
                route,
                null,
                payload,
                cls,
                () -> {
                    Deal deal = dealService.createDeal(userId, null);
                    dealService.setStatus(deal.getId(), DealStatus.ACTIVE_NEGOTIATION);
                    return ResponseEntity.ok(Map.of("dealId", deal.getId().toString()));
                }
        );
    }

    @PostMapping("/intake")
    public ResponseEntity<Map<String, Object>> intake(
            Principal principal,
            @RequestBody IntakeRequest req) {
        UUID userId = resolveUserId(principal);
        StructuredTermsDTO terms = intakeExtractionService.extractDealIntake(req.rawPrompt(), req.currency() == null ? "USD" : req.currency());
        termsValidator.validate(terms);
        Deal deal = dealService.createDeal(userId, null);
        // generate invite token
        String inviteToken = UUID.randomUUID().toString();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(inviteToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String hashHex = HexFormat.of().formatHex(hash);
            deal.setInviteTokenHash(hashHex);
            dealRepository.save(deal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate invite token hash", e);
        }
        dealService.setStatus(deal.getId(), DealStatus.ACTIVE_NEGOTIATION);
        Map<String, Object> body = Map.of(
                "dealId", deal.getId().toString(),
                "terms", terms,
                "inviteToken", inviteToken
        );
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<Void> joinDeal(
            @PathVariable("id") UUID dealId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody JoinRequest req,
            Principal principal) {
        UUID userId = resolveUserId(principal);
        String route = "/api/v1/deals/" + dealId + "/join";
        return idempotencyService.execute(
                idempotencyKey,
                route,
                dealId,
                req,
                Void.class,
                () -> {
                    Deal deal = dealService.getDeal(dealId);
                    if (deal.getBuyerUserId().equals(userId)) {
                        throw new IllegalArgumentException("Buyer cannot join own deal");
                    }
                    deal.setSellerUserId(userId);
                    // Persist via repository - simplified
                    dealService.setStatus(dealId, DealStatus.ACTIVE_NEGOTIATION);
                    return ResponseEntity.ok().build();
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

    public record JoinRequest(String inviteToken) {}
    public record IntakeRequest(String rawPrompt, String currency) {}
}
