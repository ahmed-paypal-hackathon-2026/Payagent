package com.payagent.settlement.persistence;

import com.payagent.common.contracts.InvoiceLifecycleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class InvoiceEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "deal_id", nullable = false)
    private UUID dealId;

    @Column(name = "agreement_version", nullable = false)
    private int agreementVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 32)
    private InvoiceLifecycleStatus lifecycleStatus;

    @Column(name = "paypal_invoice_id", length = 128)
    private String paypalInvoiceId;

    @Column(name = "paypal_invoice_number", length = 128)
    private String paypalInvoiceNumber;

    @Column(name = "paypal_invoice_view_url")
    private String paypalInvoiceViewUrl;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public InvoiceEntity() {}

    public InvoiceEntity(UUID id, UUID dealId, int agreementVersion, InvoiceLifecycleStatus lifecycleStatus,
                         String paypalInvoiceId, String paypalInvoiceNumber, String paypalInvoiceViewUrl,
                         long amountMinor, String currency) {
        this.id = id;
        this.dealId = dealId;
        this.agreementVersion = agreementVersion;
        this.lifecycleStatus = lifecycleStatus;
        this.paypalInvoiceId = paypalInvoiceId;
        this.paypalInvoiceNumber = paypalInvoiceNumber;
        this.paypalInvoiceViewUrl = paypalInvoiceViewUrl;
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

    public InvoiceLifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(InvoiceLifecycleStatus lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public String getPaypalInvoiceId() {
        return paypalInvoiceId;
    }

    public void setPaypalInvoiceId(String paypalInvoiceId) {
        this.paypalInvoiceId = paypalInvoiceId;
    }

    public String getPaypalInvoiceNumber() {
        return paypalInvoiceNumber;
    }

    public void setPaypalInvoiceNumber(String paypalInvoiceNumber) {
        this.paypalInvoiceNumber = paypalInvoiceNumber;
    }

    public String getPaypalInvoiceViewUrl() {
        return paypalInvoiceViewUrl;
    }

    public void setPaypalInvoiceViewUrl(String paypalInvoiceViewUrl) {
        this.paypalInvoiceViewUrl = paypalInvoiceViewUrl;
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
