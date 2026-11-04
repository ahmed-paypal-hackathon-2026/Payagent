package com.payagent.agreements;

import com.payagent.common.contracts.AgreementSnapshotDTO;
import com.payagent.deals.DealService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deals/{dealId}")
public class AgreementController {

    private final AgreementService service;
    private final DealService dealService;

    public AgreementController(AgreementService service, DealService dealService) {
        this.service = service;
        this.dealService = dealService;
    }

    @PostMapping("/agreements")
    public ResponseEntity<AgreementSnapshotDTO> proposeAgreement(
            @PathVariable UUID dealId,
            @RequestBody com.payagent.common.contracts.StructuredTermsDTO terms,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            dealService.requireMembership(dealId, userId);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        try {
            AgreementSnapshotDTO snapshot = service.proposeAgreement(dealId, terms);
            return ResponseEntity.status(HttpStatus.CREATED).body(snapshot);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        }
    }

    @PostMapping("/acceptance")
    public ResponseEntity<AgreementSnapshotDTO> acceptAgreement(
            @PathVariable UUID dealId,
            @RequestBody AcceptanceRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId) {

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        com.payagent.common.contracts.PartyRole role;
        try {
            role = dealService.requireMembership(dealId, userId);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        try {
            AgreementSnapshotDTO snapshot = service.acceptAgreement(
                    dealId, request.agreementVersion(), request.termsHashSha256(), role.name());
            return ResponseEntity.ok(snapshot);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    public record AcceptanceRequest(int agreementVersion, String termsHashSha256) {}
}
