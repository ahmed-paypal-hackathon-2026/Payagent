package com.payagent.agreements;

import com.payagent.common.contracts.AgreementSnapshotDTO;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.settlement.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deals/{id}/agreements")
public class AgreementController {

    private final AgreementService agreementService;
    private final com.payagent.deals.DealService dealService;
    private final IdempotencyService idempotencyService;

    public AgreementController(AgreementService agreementService, com.payagent.deals.DealService dealService, IdempotencyService idempotencyService) {
        this.agreementService = agreementService;
        this.dealService = dealService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/propose")
    public ResponseEntity<AgreementSnapshotDTO> proposeAgreement(
            @PathVariable("id") UUID dealId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StructuredTermsDTO terms,
            @RequestAttribute(value = "userId", required = false) UUID userIdAttr,
            Principal principal) {
        UUID userId = resolveUserId(userIdAttr, principal);
        dealService.requireMembership(dealId, userId);
        String route = "/api/v1/deals/" + dealId + "/agreements/propose";
        return idempotencyService.execute(
                idempotencyKey,
                route,
                dealId,
                terms,
                AgreementSnapshotDTO.class,
                () -> ResponseEntity.ok(agreementService.proposeAgreement(dealId, terms))
        );
    }

    @PostMapping("/{version}/accept")
    public ResponseEntity<AgreementSnapshotDTO> acceptAgreement(
            @PathVariable("id") UUID dealId,
            @PathVariable("version") int version,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody AcceptRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userIdAttr,
            Principal principal) {
        UUID userId = resolveUserId(userIdAttr, principal);
        com.payagent.common.contracts.PartyRole role = dealService.requireMembership(dealId, userId);
        String route = "/api/v1/deals/" + dealId + "/agreements/" + version + "/accept";
        AcceptPayload payload = new AcceptPayload(version, request.termsHashSha256(), role.name());
        return idempotencyService.execute(
                idempotencyKey,
                route,
                dealId,
                payload,
                AgreementSnapshotDTO.class,
                () -> ResponseEntity.ok(agreementService.acceptAgreement(dealId, version, request.termsHashSha256(), role.name()))
        );
    }

    private UUID resolveUserId(UUID userIdAttr, Principal principal) {
        if (userIdAttr != null) {
            return userIdAttr;
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid principal");
        }
    }

    public record AcceptRequest(String termsHashSha256) {}
    public record AcceptPayload(int version, String termsHashSha256, String role) {}
}
