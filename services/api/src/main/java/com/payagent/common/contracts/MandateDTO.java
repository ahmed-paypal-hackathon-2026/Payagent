package com.payagent.common.contracts;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record MandateDTO(
        @NotBlank String mandateId,
        @NotBlank String dealId,
        @NotNull PartyRole partyRole,
        @Min(1) int version,
        @NotBlank String currency,
        @Min(0) long limitPriceMinorUnits,
        @Min(0) long targetPriceMinorUnits,
        @Min(1) int limitDeliveryDays,
        @Min(1) int targetDeliveryDays,
        @Min(0) int limitRevisions,
        @Min(0) int targetRevisions,
        @NotNull List<String> mustHaveDeliverables,
        @NotNull List<String> forbiddenTerms,
        @NotNull NegotiationMode mode,
        @Min(1) int maxRounds,
        @NotBlank String expiresAt) {}
