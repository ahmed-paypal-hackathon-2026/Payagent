package com.payagent.deals;

import com.payagent.ai.intake.IntakeExtractionService;
import com.payagent.ai.negotiation.StructuredTermsValidator;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.settlement.IdempotencyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
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
            @RequestAttribute(value = "userId", required = false) UUID userIdAttr,
            Principal principal) {
        UUID userId = userIdAttr;
        if (userId == null && principal != null) {
            try {
                userId = UUID.fromString(principal.getName());
            } catch (Exception ignored) {}
        }
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        final UUID finalUserId = userId;
        String route = "/api/v1/deals";
        String payload = finalUserId.toString();
        @SuppressWarnings("unchecked")
        Class<Map<String,String>> cls = (Class<Map<String,String>>)(Class<?>)Map.class;
        return idempotencyService.execute(
                idempotencyKey,
                route,
                null,
                payload,
                cls,
                () -> {
                    Deal deal = dealService.createDeal(finalUserId, null);
                    dealService.setStatus(deal.getId(), DealStatus.ACTIVE_NEGOTIATION);
                    return ResponseEntity.ok(Map.of("dealId", deal.getId().toString()));
                }
        );
    }

    @PostMapping("/intake")
    public ResponseEntity<Map<String, Object>> intake(
            @RequestAttribute(value = "userId", required = false) UUID userIdAttr,
            Principal principal,
            @RequestBody IntakeRequest req) {
        UUID userId = userIdAttr;
        if (userId == null && principal != null) {
            try {
                userId = UUID.fromString(principal.getName());
            } catch (Exception ignored) {}
        }
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        StructuredTermsDTO terms = intakeExtractionService.extractDealIntake(req.rawPrompt(), req.currency() == null ? "USD" : req.currency());
        termsValidator.validate(terms);
        String inviteToken = UUID.randomUUID().toString();
        Deal deal = dealService.createDealWithInvite(userId, inviteToken);
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
            @RequestAttribute(value = "userId", required = false) UUID userIdAttr,
            Principal principal) {
        UUID userId = userIdAttr;
        if (userId == null && principal != null) {
            try {
                userId = UUID.fromString(principal.getName());
            } catch (Exception ignored) {}
        }
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        final UUID finalUserId = userId;
        final UUID finalDealId = dealId;
        String route = "/api/v1/deals/" + dealId + "/join";
        return idempotencyService.execute(
                idempotencyKey,
                route,
                finalDealId,
                req,
                Void.class,
                () -> {
                    try {
                        dealService.joinDeal(finalDealId, finalUserId, req.inviteToken());
                        return ResponseEntity.ok().build();
                    } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().build();
                    } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                    } catch (IllegalStateException e) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).build();
                    }
                }
        );
    }

    public record JoinRequest(String inviteToken) {}
    public record IntakeRequest(String rawPrompt, String currency) {}
}
