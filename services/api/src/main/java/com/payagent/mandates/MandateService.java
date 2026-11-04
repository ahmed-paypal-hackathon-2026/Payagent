package com.payagent.mandates;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import com.payagent.deals.DealService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MandateService {
    private final MandateRepository repository;
    private final DealService dealService;
    private final ObjectMapper objectMapper;

    public MandateService(MandateRepository repository, DealService dealService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.dealService = dealService;
        this.objectMapper = objectMapper;
    }

    @Transactional(noRollbackFor = {SecurityException.class, IllegalArgumentException.class})
    public MandateDTO createMandate(UUID dealId, UUID userId, MandateCreateRequest req) {
        PartyRole role = dealService.requireMembership(dealId, userId);
        validateRequest(req, role);
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
                req.mustHaveDeliverables(),
                req.forbiddenTerms(),
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

    @Transactional
    public MandateDTO createMandate(UUID dealId, PartyRole role, MandateDTO dto) {
        if (dto.version() < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
        OffsetDateTime expires = OffsetDateTime.parse(dto.expiresAt());
        if (!expires.isAfter(OffsetDateTime.now())) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }
        Mandate mandate = new Mandate(UUID.randomUUID(), dealId, role, dto.version(),
                dto.currency(), dto.limitPriceMinorUnits(), dto.targetPriceMinorUnits(),
                dto.limitDeliveryDays(), dto.targetDeliveryDays(),
                dto.limitRevisions(), dto.targetRevisions(),
                dto.mustHaveDeliverables(), dto.forbiddenTerms(),
                dto.mode(), dto.maxRounds(), expires);
        mandate.validate();
        if (repository.findByDealIdAndPartyRoleAndVersion(dealId, role, dto.version()).isPresent()) {
            throw new IllegalArgumentException("Duplicate version");
        }
        Mandate saved = repository.save(mandate);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public MandateDTO getLatestMandate(UUID dealId, PartyRole role) {
        UUID userId = resolveUserId(dealId, role);
        return getMandateForUser(dealId, userId);
    }

    private UUID resolveUserId(UUID dealId, PartyRole role) {
        com.payagent.deals.Deal deal = dealService.getDeal(dealId);
        if (role == PartyRole.BUYER) {
            UUID buyer = deal.getBuyerUserId();
            if (buyer == null) {
                throw new IllegalArgumentException("Buyer user not set for deal");
            }
            return buyer;
        }
        if (role == PartyRole.SELLER) {
            UUID seller = deal.getSellerUserId();
            if (seller == null) {
                throw new IllegalArgumentException("Seller user not set for deal");
            }
            return seller;
        }
        throw new IllegalArgumentException("Unknown PartyRole: " + role);
    }

    private void validateRequest(MandateCreateRequest req, PartyRole role) {
        if (req.limitPriceMinorUnits() <= 0 || req.targetPriceMinorUnits() <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (role == PartyRole.BUYER && req.targetPriceMinorUnits() > req.limitPriceMinorUnits()) {
            throw new IllegalArgumentException("Target price cannot exceed limit for BUYER");
        }
        if (role == PartyRole.SELLER && req.targetPriceMinorUnits() < req.limitPriceMinorUnits()) {
            throw new IllegalArgumentException("Target price cannot be below limit for SELLER");
        }
    }

    private MandateDTO toDto(Mandate m) {
        try {
            List<String> mustHave = parseJsonList(m.getMustHaveDeliverablesJson());
            List<String> forbidden = parseJsonList(m.getForbiddenTermsJson());
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
                    mustHave,
                    forbidden,
                    m.getMode(),
                    m.getMaxRounds(),
                    m.getExpiresAt().toString()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to map Mandate", e);
        }
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            // Handle possible double-encoding where DB returns a JSON string containing JSON
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json);
            if (node.isTextual()) {
                // Unwrap one level of string encoding
                json = node.asText();
                return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }
            return objectMapper.convertValue(node, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            // Fallback to direct read
            try {
                return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            } catch (Exception ex) {
                return List.of();
            }
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
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
