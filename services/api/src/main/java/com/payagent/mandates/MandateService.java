package com.payagent.mandates;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import com.payagent.deals.DealService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class MandateService {
    private final MandateRepository repository;
    private final DealService dealService;
    private final ObjectMapper objectMapper;
    private final MandateValidator mandateValidator;

    public MandateService(MandateRepository repository, DealService dealService, ObjectMapper objectMapper,
                          MandateValidator mandateValidator) {
        this.repository = repository;
        this.dealService = dealService;
        this.objectMapper = objectMapper;
        this.mandateValidator = mandateValidator;
    }

    @Transactional
    public MandateDTO createMandate(UUID dealId, UUID userId, MandateCreateRequest req) {
        PartyRole role = dealService.requireMembership(dealId, userId);
        dealService.getDealForUpdate(dealId);
        mandateValidator.validate(req, role);
        int nextVersion = repository.findTopByDealIdAndPartyRoleOrderByVersionDesc(dealId, role)
                .map(m -> m.getVersion() + 1)
                .orElse(1);
        Mandate mandate = new Mandate(
                UUID.randomUUID(),
                dealId,
                role,
                nextVersion,
                req.currency(),
                req.limitPriceMinorUnits(),
                req.targetPriceMinorUnits(),
                req.limitDeliveryDays(),
                req.targetDeliveryDays(),
                req.limitRevisions(),
                req.targetRevisions(),
                toJson(req.mustHaveDeliverables()),
                toJson(req.forbiddenTerms()),
                req.mode(),
                req.maxRounds(),
                req.expiresAt()
        );
        Mandate saved = repository.save(mandate);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public MandateDTO getMandateForUser(UUID dealId, UUID userId) {
        PartyRole role = dealService.requireMembership(dealId, userId);
        Mandate mandate = repository.findTopByDealIdAndPartyRoleOrderByVersionDesc(dealId, role)
                .orElseThrow(() -> new IllegalArgumentException("Mandate not found"));
        return toDto(mandate);
    }

    public MandateDTO getMandateForUserOrNull(UUID dealId, UUID userId) {
        PartyRole role = dealService.requireMembership(dealId, userId);
        return repository.findTopByDealIdAndPartyRoleOrderByVersionDesc(dealId, role)
                .map(this::toDto)
                .orElse(null);
    }

    private MandateDTO toDto(Mandate m) {        try {
            return new MandateDTO(
                    m.getId().toString(),
                    m.getDealId().toString(),
                    m.getPartyRole(),
                    m.getVersion(),
                    m.getCurrency(),
                    m.getLimitPriceMinor(),
                    m.getTargetPriceMinor(),
                    m.getLimitDeliveryDays(),
                    m.getTargetDeliveryDays(),
                    m.getLimitRevisions(),
                    m.getTargetRevisions(),
                            fromJson(m.getMustHaveDeliverablesJson()),
                    fromJson(m.getForbiddenTermsJson()),
                    m.getMode(),
                    m.getMaxRounds(),
                    m.getExpiresAt().toString()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to map Mandate", e);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private java.util.List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return java.util.Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, java.util.List.class);
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    public record MandateCreateRequest(
            String currency,
            long limitPriceMinorUnits,
            long targetPriceMinorUnits,
            int limitDeliveryDays,
            int targetDeliveryDays,
            int limitRevisions,
            int targetRevisions,
            java.util.List<String> mustHaveDeliverables,
            java.util.List<String> forbiddenTerms,
            NegotiationMode mode,
            int maxRounds,
            OffsetDateTime expiresAt
    ) {}
}
