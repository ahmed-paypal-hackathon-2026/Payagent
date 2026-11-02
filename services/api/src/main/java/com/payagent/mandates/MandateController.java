package com.payagent.mandates;

import com.payagent.common.contracts.MandateDTO;
import com.payagent.settlement.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deals/{id}/mandates")
public class MandateController {

    private final MandateService service;
    private final IdempotencyService idempotencyService;

    public MandateController(MandateService service, IdempotencyService idempotencyService) {
        this.service = service;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public ResponseEntity<MandateDTO> createMandate(
            @PathVariable("id") UUID dealId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody MandateService.MandateCreateRequest req,
            @RequestAttribute(value = "userId", required = false) UUID userIdAttr,
            Principal principal) {
        UUID userId = resolveUserId(userIdAttr, principal);
        String route = "/api/v1/deals/" + dealId + "/mandates";
        return idempotencyService.execute(
                idempotencyKey,
                route,
                dealId,
                req,
                MandateDTO.class,
                () -> ResponseEntity.ok(service.createMandate(dealId, userId, req))
        );
    }

    @GetMapping
    public ResponseEntity<MandateDTO> getMandate(
            @PathVariable("id") UUID dealId,
            @RequestAttribute(value = "userId", required = false) UUID userIdAttr,
            Principal principal) {
        UUID userId = resolveUserId(userIdAttr, principal);
        MandateDTO dto = service.getMandateForUser(dealId, userId);
        return ResponseEntity.ok(dto);
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
}
