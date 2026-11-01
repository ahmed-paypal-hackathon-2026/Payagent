package com.payagent.disputes.persistence;

import com.payagent.common.contracts.DisputeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "disputes")
public class DisputeEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "deal_id", nullable = false)
    private UUID dealId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 64, nullable = false)
    private DisputeStatus status;

    @Column(name = "reason", columnDefinition = "text", nullable = false)
    private String reason;

    @Column(name = "resolution_request", columnDefinition = "text")
    private String resolutionRequest;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public DisputeEntity() {}

    public DisputeEntity(UUID id, UUID dealId, DisputeStatus status, String reason, String resolutionRequest) {
        this.id = id;
        this.dealId = dealId;
        this.status = status;
        this.reason = reason;
        this.resolutionRequest = resolutionRequest;
        this.openedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDealId() {
        return dealId;
    }

    public void setDealId(UUID dealId) {
        this.dealId = dealId;
    }

    public DisputeStatus getStatus() {
        return status;
    }

    public void setStatus(DisputeStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getResolutionRequest() {
        return resolutionRequest;
    }

    public void setResolutionRequest(String resolutionRequest) {
        this.resolutionRequest = resolutionRequest;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(Instant openedAt) {
        this.openedAt = openedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
