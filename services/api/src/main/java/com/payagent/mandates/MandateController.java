package com.payagent.mandates;

import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.PartyRole;
import com.payagent.deals.DealService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deals/{dealId}/mandates")
public class MandateController {

    private final MandateService service;
    private final DealService dealService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper mapper;

    public MandateController(MandateService service, DealService dealService,
                             @Qualifier("mandateIdempotencyService") IdempotencyService idempotencyService,
                             ObjectMapper mapper) {
        this.service = service;
        this.dealService = dealService;
        this.idempotencyService = idempotencyService;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<MandateDTO> createMandate(
            HttpServletRequest request,
            @PathVariable UUID dealId,
            @Valid @RequestBody MandateService.MandateCreateRequest req,
            @RequestAttribute(value = "userId", required = false) UUID userId) {

        String idempotencyKey = request.getHeader("Idempotency-Key");

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String route = "POST /api/v1/deals/" + dealId + "/mandates";
        byte[] requestBody;
        try {
            requestBody = mapper.writeValueAsBytes(req);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        IdempotencyService.IdempotentResponse result =
                idempotencyService.execute(idempotencyKey, route, dealId, requestBody,
                        () -> {
                            try {
                                MandateDTO created = service.createMandate(dealId, userId, req);
                                return new IdempotencyService.IdempotentResponse(
                                        HttpStatus.CREATED.value(),
                                        mapper.writeValueAsString(created),
                                        false);
                            } catch (SecurityException e) {
                                return new IdempotencyService.IdempotentResponse(
                                        HttpStatus.FORBIDDEN.value(), null, false);
                            } catch (IllegalArgumentException e) {
                                return new IdempotencyService.IdempotentResponse(
                                        HttpStatus.UNPROCESSABLE_ENTITY.value(), null, false);
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
                MandateDTO replayed = mapper.readValue(result.body(), MandateDTO.class);
                return ResponseEntity.status(HttpStatus.CREATED).body(replayed);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }
        if (result.body() != null) {
            try {
                MandateDTO created = mapper.readValue(result.body(), MandateDTO.class);
                return ResponseEntity.status(HttpStatus.CREATED).body(created);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }
        return ResponseEntity.status(HttpStatus.valueOf(result.status())).build();
    }

    @GetMapping("/me")
    public ResponseEntity<MandateDTO> getMyMandate(
            @PathVariable UUID dealId,
            @RequestAttribute(value = "userId", required = false) UUID userId) {

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            MandateDTO mandate = service.getMandateForUser(dealId, userId);
            return ResponseEntity.ok(mandate);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
