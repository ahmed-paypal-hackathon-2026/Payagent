package com.payagent.settlement;

import com.payagent.common.contracts.SettlementStateDTO;
import com.payagent.settlement.model.AuthorizeSettlementRequest;
import com.payagent.settlement.model.VoidSettlementRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deals/{id}/settlement")
public class SettlementController {

    private final SettlementService settlementService;
    private final IdempotencyService idempotencyService;

    public SettlementController(SettlementService settlementService, IdempotencyService idempotencyService) {
        this.settlementService = settlementService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/initiate")
    public ResponseEntity<SettlementStateDTO> initiateSettlement(
            @PathVariable("id") UUID dealId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(value = "userEmail", required = false) String userEmail,
            Principal principal) {
        String callerEmail = resolveCallerEmail(userEmail, principal);
        String route = "/api/v1/deals/" + dealId + "/settlement/initiate";

        return idempotencyService.execute(
                idempotencyKey,
                route,
                dealId,
                "initiate",
                SettlementStateDTO.class,
                () -> ResponseEntity.ok(settlementService.initiateSettlement(dealId, idempotencyKey, callerEmail))
        );
    }

    @PostMapping("/authorize")
    public ResponseEntity<SettlementStateDTO> authorizeSettlement(
            @PathVariable("id") UUID dealId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) AuthorizeSettlementRequest request,
            @RequestAttribute(value = "userEmail", required = false) String userEmail,
            Principal principal) {
        String callerEmail = resolveCallerEmail(userEmail, principal);
        String route = "/api/v1/deals/" + dealId + "/settlement/authorize";
        String orderId = request != null ? request.paypalOrderId() : null;

        return idempotencyService.execute(
                idempotencyKey,
                route,
                dealId,
                request,
                SettlementStateDTO.class,
                () -> ResponseEntity.ok(settlementService.authorizeSettlement(dealId, orderId, idempotencyKey, callerEmail))
        );
    }

    @PostMapping("/void")
    public ResponseEntity<SettlementStateDTO> voidSettlement(
            @PathVariable("id") UUID dealId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) VoidSettlementRequest request,
            @RequestAttribute(value = "userEmail", required = false) String userEmail,
            Principal principal) {
        String callerEmail = resolveCallerEmail(userEmail, principal);
        String reason = request != null ? request.reason() : "Settlement void requested";
        String route = "/api/v1/deals/" + dealId + "/settlement/void";

        return idempotencyService.execute(
                idempotencyKey,
                route,
                dealId,
                request,
                SettlementStateDTO.class,
                () -> ResponseEntity.ok(settlementService.voidSettlement(dealId, reason, idempotencyKey, callerEmail))
        );
    }

    @GetMapping
    public ResponseEntity<SettlementStateDTO> getSettlementState(
            @PathVariable("id") UUID dealId,
            @RequestAttribute(value = "userEmail", required = false) String userEmail,
            Principal principal) {
        String callerEmail = resolveCallerEmail(userEmail, principal);
        return ResponseEntity.ok(settlementService.getSettlementState(dealId, callerEmail));
    }

    private String resolveCallerEmail(String userEmail, Principal principal) {
        if (userEmail != null && !userEmail.isBlank()) {
            return userEmail;
        }
        return principal != null ? principal.getName() : null;
    }
}
