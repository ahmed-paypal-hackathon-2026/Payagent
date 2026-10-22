package com.payagent.common.contracts;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SettlementStateDTO(
        @NotBlank String dealId,
        @Min(1) int agreementVersion,
        @NotNull InvoiceLifecycleStatus invoiceStatus,
        String paypalInvoiceId,
        String paypalInvoiceNumber,
        String paypalInvoiceViewUrl,
        @NotNull PaymentLifecycleStatus paymentStatus,
        String paypalOrderId,
        String paypalAuthorizationId,
        String paypalCaptureId,
        String authorizationExpiresAt,
        @Min(0) long amountMinorUnits,
        @NotBlank String currency,
        String lastReconciledAt) {}
