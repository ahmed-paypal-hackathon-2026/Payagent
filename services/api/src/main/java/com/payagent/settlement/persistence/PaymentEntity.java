package com.payagent.settlement.persistence;

import com.payagent.common.contracts.PaymentLifecycleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "deal_id", nullable = false)
    private UUID dealId;

    @Column(name = "agreement_version", nullable = false)
    private int agreementVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 32)
    private PaymentLifecycleStatus lifecycleStatus;

    @Column(name = "paypal_order_id", length = 128)
    private String paypalOrderId;

    @Column(name = "paypal_authorization_id", length = 128)
    private String paypalAuthorizationId;

    @Column(name = "paypal_capture_id", length = 128)
    private String paypalCaptureId;

    @Column(name = "authorization_expires_at")
    private Instant authorizationExpiresAt;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public PaymentEntity() {}

    public PaymentEntity(UUID id, UUID dealId, int agreementVersion, PaymentLifecycleStatus lifecycleStatus,
                         String paypalOrderId, String paypalAuthorizationId, String paypalCaptureId,
                         Instant authorizationExpiresAt, long amountMinor, String currency) {
        this.id = id;
        this.dealId = dealId;
        this.agreementVersion = agreementVersion;
        this.lifecycleStatus = lifecycleStatus;
        this.paypalOrderId = paypalOrderId;
        this.paypalAuthorizationId = paypalAuthorizationId;
        this.paypalCaptureId = paypalCaptureId;
        this.authorizationExpiresAt = authorizationExpiresAt;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.updatedAt = Instant.now();
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

    public int getAgreementVersion() {
        return agreementVersion;
    }

    public void setAgreementVersion(int agreementVersion) {
        this.agreementVersion = agreementVersion;
    }

    public PaymentLifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(PaymentLifecycleStatus lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public String getPaypalOrderId() {
        return paypalOrderId;
    }

    public void setPaypalOrderId(String paypalOrderId) {
        this.paypalOrderId = paypalOrderId;
    }

    public String getPaypalAuthorizationId() {
        return paypalAuthorizationId;
    }

    public void setPaypalAuthorizationId(String paypalAuthorizationId) {
        this.paypalAuthorizationId = paypalAuthorizationId;
    }

    public String getPaypalCaptureId() {
        return paypalCaptureId;
    }

    public void setPaypalCaptureId(String paypalCaptureId) {
        this.paypalCaptureId = paypalCaptureId;
    }

    public Instant getAuthorizationExpiresAt() {
        return authorizationExpiresAt;
    }

    public void setAuthorizationExpiresAt(Instant authorizationExpiresAt) {
        this.authorizationExpiresAt = authorizationExpiresAt;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(long amountMinor) {
        this.amountMinor = amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
