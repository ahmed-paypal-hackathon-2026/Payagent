package com.payagent.agreements;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agreements", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"deal_id", "version"})
})
public class Agreement {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "deal_id", nullable = false, columnDefinition = "UUID")
    private UUID dealId;

    @Column(nullable = false)
    private Integer version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "canonical_terms_json", nullable = false, columnDefinition = "jsonb")
    private String canonicalTermsJson;

    @Column(name = "terms_hash_sha256", nullable = false, length = 64)
    private String termsHashSha256;

    @Column(name = "buyer_accepted_at")
    private OffsetDateTime buyerAcceptedAt;

    @Column(name = "seller_accepted_at")
    private OffsetDateTime sellerAcceptedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Agreement() {}

    public Agreement(UUID id, UUID dealId, Integer version, String canonicalTermsJson, String termsHashSha256) {
        this.id = id;
        this.dealId = dealId;
        this.version = version;
        this.canonicalTermsJson = canonicalTermsJson;
        this.termsHashSha256 = termsHashSha256;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getDealId() { return dealId; }
    public Integer getVersion() { return version; }
    public String getCanonicalTermsJson() { return canonicalTermsJson; }
    public String getTermsHashSha256() { return termsHashSha256; }
    public OffsetDateTime getBuyerAcceptedAt() { return buyerAcceptedAt; }
    public OffsetDateTime getSellerAcceptedAt() { return sellerAcceptedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void acceptBy(String role) {
        OffsetDateTime now = OffsetDateTime.now();
        if ("BUYER".equals(role)) {
            this.buyerAcceptedAt = now;
        } else if ("SELLER".equals(role)) {
            this.sellerAcceptedAt = now;
        }
    }

    public void clearAcceptances() {
        this.buyerAcceptedAt = null;
        this.sellerAcceptedAt = null;
    }
}
