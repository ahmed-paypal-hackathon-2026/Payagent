package com.payagent.settlement;

import com.payagent.common.contracts.SettlementStateDTO;
import com.payagent.settlement.model.AcceptDeliverablesRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deals/{id}")
public class DeliverablesAcceptanceController {

    private final SettlementService settlementService;
    private final IdempotencyService idempotencyService;

    public DeliverablesAcceptanceController(SettlementService settlementService, IdempotencyService idempotencyService) {
        this.settlementService = settlementService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/accept-deliverables")
    public ResponseEntity<SettlementStateDTO> acceptDeliverables(
            @PathVariable("id") UUID dealId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody(required = false) AcceptDeliverablesRequest request,
            Principal principal) {
        String callerEmail = principal != null ? principal.getName() : null;
        int version = request != null && request.evidenceVersion() > 0 ? request.evidenceVersion() : 1;
        String route = "/api/v1/deals/" + dealId + "/accept-deliverables";

        return idempotencyService.execute(
                idempotencyKey,
                route,
                dealId,
                request,
                SettlementStateDTO.class,
                () -> ResponseEntity.ok(settlementService.acceptDeliverables(dealId, version, idempotencyKey, callerEmail))
        );
    }
}
