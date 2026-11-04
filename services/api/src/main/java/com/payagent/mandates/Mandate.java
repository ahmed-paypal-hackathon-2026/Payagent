package com.payagent.mandates;

import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import jakarta.persistence.*;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "mandates", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"deal_id", "party_role", "version"})
})
public class Mandate {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "deal_id", nullable = false, columnDefinition = "UUID")
    private UUID dealId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_role", nullable = false, length = 16)
    private PartyRole partyRole;

    @Min(1)
    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "limit_price_minor", nullable = false)
    private Long limitPriceMinor;

    @Column(name = "target_price_minor", nullable = false)
    private Long targetPriceMinor;

    @Column(name = "limit_delivery_days", nullable = false)
    private Integer limitDeliveryDays;

    @Column(name = "target_delivery_days", nullable = false)
    private Integer targetDeliveryDays;

    @Column(name = "limit_revisions", nullable = false)
    private Integer limitRevisions;

    @Column(name = "target_revisions", nullable = false)
    private Integer targetRevisions;

    @Column(name = "must_have_deliverables", columnDefinition = "jsonb", nullable = false)
    private String mustHaveDeliverablesJson;

    @Column(name = "forbidden_terms", columnDefinition = "jsonb", nullable = false)
    private String forbiddenTermsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NegotiationMode mode;

    @Column(name = "max_rounds", nullable = false)
    private Integer maxRounds;

    @Future
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Mandate() {}

    public Mandate(UUID id, UUID dealId, PartyRole partyRole, Integer version, String currency,
                   Long limitPriceMinor, Long targetPriceMinor, Integer limitDeliveryDays,
                   Integer targetDeliveryDays, Integer limitRevisions, Integer targetRevisions,
                   List<String> mustHaveDeliverables, List<String> forbiddenTerms,
                   NegotiationMode mode, Integer maxRounds, OffsetDateTime expiresAt) {
        this.id = id;
        this.dealId = dealId;
        this.partyRole = partyRole;
        this.version = version;
        this.currency = currency;
        this.limitPriceMinor = limitPriceMinor;
        this.targetPriceMinor = targetPriceMinor;
        this.limitDeliveryDays = limitDeliveryDays;
        this.targetDeliveryDays = targetDeliveryDays;
        this.limitRevisions = limitRevisions;
        this.targetRevisions = targetRevisions;
        this.mustHaveDeliverablesJson = toJson(mustHaveDeliverables);
        this.forbiddenTermsJson = toJson(forbiddenTerms);
        this.mode = mode;
        this.maxRounds = maxRounds;
        this.expiresAt = expiresAt;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getDealId() { return dealId; }
    public PartyRole getPartyRole() { return partyRole; }
    public Integer getVersion() { return version; }
    public String getCurrency() { return currency; }
    public Long getLimitPriceMinor() { return limitPriceMinor; }
    public Long getTargetPriceMinor() { return targetPriceMinor; }
    public Integer getLimitDeliveryDays() { return limitDeliveryDays; }
    public Integer getTargetDeliveryDays() { return targetDeliveryDays; }
    public Integer getLimitRevisions() { return limitRevisions; }
    public Integer getTargetRevisions() { return targetRevisions; }
    public String getMustHaveDeliverablesJson() { return mustHaveDeliverablesJson; }
    public String getForbiddenTermsJson() { return forbiddenTermsJson; }
    public NegotiationMode getMode() { return mode; }
    public Integer getMaxRounds() { return maxRounds; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    /**
     * Validates mandate invariants per plan v2.docx Section 2 and sub-plan Section 3:
     * version >= 1, expiresAt in the future, and price direction per role.
     * Buyer: limitPrice >= targetPrice. Seller: limitPrice <= targetPrice.
     */
    public void validate() {
        if (version == null || version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
        if (expiresAt == null || !expiresAt.isAfter(OffsetDateTime.now())) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }
        if (limitPriceMinor == null || targetPriceMinor == null) {
            throw new IllegalArgumentException("limitPriceMinor and targetPriceMinor must not be null");
        }
        if (partyRole == PartyRole.BUYER && limitPriceMinor < targetPriceMinor) {
            throw new IllegalArgumentException("Buyer limit price must be >= target price");
        }
        if (partyRole == PartyRole.SELLER && limitPriceMinor > targetPriceMinor) {
            throw new IllegalArgumentException("Seller limit price must be <= target price");
        }
    }

    /**
     * Deserializes a JSON array string into a list of strings using Jackson.
     * Used for JSONB round-tripping of mustHaveDeliverables and forbiddenTerms.
     */
    public static List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON list: " + json, e);
        }
    }

    private static String toJson(List<String> list) {
        if (list == null) return "[]";
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(list);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize list", e);
        }
    }

}
