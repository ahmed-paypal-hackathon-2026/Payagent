package com.payagent.deals;

import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.NegotiationStopReason;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "deals")
public class Deal {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID id;

    @Column(name = "buyer_user_id", nullable = false, columnDefinition = "UUID")
    private UUID buyerUserId;

    @Column(name = "seller_user_id", columnDefinition = "UUID")
    private UUID sellerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 64)
    private DealStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_reason", nullable = false, length = 64)
    private NegotiationStopReason stopReason;

    @Column(name = "invite_token_hash", length = 128)
    private String inviteTokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Deal() {}

    public Deal(UUID id, UUID buyerUserId, UUID sellerUserId, DealStatus status) {
        this.id = id;
        this.buyerUserId = buyerUserId;
        this.sellerUserId = sellerUserId;
        this.status = status == null ? DealStatus.DRAFT : status;
        this.stopReason = NegotiationStopReason.NONE;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getBuyerUserId() { return buyerUserId; }
    public UUID getSellerUserId() { return sellerUserId; }
    public DealStatus getStatus() { return status; }
    public NegotiationStopReason getStopReason() { return stopReason; }
    public String getInviteTokenHash() { return inviteTokenHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setSellerUserId(UUID sellerUserId) { this.sellerUserId = sellerUserId; this.updatedAt = OffsetDateTime.now(); }
    public void setStatus(DealStatus status) { this.status = status; this.updatedAt = OffsetDateTime.now(); }
    public void setStopReason(NegotiationStopReason reason) { this.stopReason = reason == null ? NegotiationStopReason.NONE : reason; this.updatedAt = OffsetDateTime.now(); }
    public void setInviteTokenHash(String hash) { this.inviteTokenHash = hash; this.updatedAt = OffsetDateTime.now(); }

    /**
     * Returns the party role of the given user on this deal, or null if the user is not a participant.
     */
    public com.payagent.common.contracts.PartyRole partyRoleOf(UUID userId) {
        if (userId == null) return null;
        if (userId.equals(buyerUserId)) return com.payagent.common.contracts.PartyRole.BUYER;
        if (sellerUserId != null && sellerUserId.equals(userId)) return com.payagent.common.contracts.PartyRole.SELLER;
        return null;
    }
}
