package com.payagent.disputes.web;

import com.payagent.disputes.model.CreateDisputeRequest;
import com.payagent.disputes.model.DisputeDTO;
import com.payagent.disputes.model.ResolveDisputeRequest;
import com.payagent.disputes.service.DisputeService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deals/{id}/disputes")
public class DisputeController {

    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @PostMapping
    public ResponseEntity<DisputeDTO> openDispute(
            @PathVariable("id") UUID dealId,
            @Valid @RequestBody CreateDisputeRequest request,
            Principal principal) {
        String callerEmail = principal != null ? principal.getName() : null;
        DisputeDTO dto = disputeService.createDispute(dealId, request, callerEmail);
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    public ResponseEntity<List<DisputeDTO>> listDisputes(
            @PathVariable("id") UUID dealId,
            Principal principal) {
        String callerEmail = principal != null ? principal.getName() : null;
        List<DisputeDTO> list = disputeService.getDisputesForDeal(dealId, callerEmail);
        return ResponseEntity.ok(list);
    }

    @PostMapping("/{disputeId}/resolve")
    public ResponseEntity<DisputeDTO> resolveDispute(
            @PathVariable("id") UUID dealId,
            @PathVariable("disputeId") UUID disputeId,
            @Valid @RequestBody ResolveDisputeRequest request,
            Principal principal) {
        String callerEmail = principal != null ? principal.getName() : null;
        DisputeDTO dto = disputeService.resolveDispute(dealId, disputeId, request, callerEmail);
        return ResponseEntity.ok(dto);
    }
}
