package com.payagent.mandates;

import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "mandates", uniqueConstraints = @UniqueConstraint(columnNames = {"deal_id", "party_role", "version"}))
public class Mandate {
    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "deal_id", nullable = false, columnDefinition = "UUID")
    private UUID dealId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_role", nullable = false)
    private PartyRole partyRole;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "must_have_deliverables", columnDefinition = "jsonb", nullable = false)
    private String mustHaveDeliverablesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "forbidden_terms", columnDefinition = "jsonb", nullable = false)
    private String forbiddenTermsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NegotiationMode mode;

    @Column(name = "max_rounds", nullable = false)
    private Integer maxRounds;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Mandate() {}

    public Mandate(UUID id, UUID dealId, PartyRole partyRole, Integer version, String currency,
                   Long limitPriceMinor, Long targetPriceMinor, Integer limitDeliveryDays,
                   Integer targetDeliveryDays, Integer limitRevisions, Integer targetRevisions,
                   String mustHaveDeliverablesJson, String forbiddenTermsJson, NegotiationMode mode,
                   Integer maxRounds, OffsetDateTime expiresAt) {
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
        this.mustHaveDeliverablesJson = mustHaveDeliverablesJson;
        this.forbiddenTermsJson = forbiddenTermsJson;
        this.mode = mode;
        this.maxRounds = maxRounds;
        this.expiresAt = expiresAt;
        this.createdAt = OffsetDateTime.now();
    }

    // Getters
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
}
