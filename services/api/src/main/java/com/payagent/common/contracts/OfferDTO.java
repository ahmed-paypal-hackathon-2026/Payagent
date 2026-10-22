package com.payagent.common.contracts;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OfferDTO(
        @NotBlank String offerId,
        @NotBlank String dealId,
        @Min(1) int roundNumber,
        @NotNull PartyRole proposerRole,
        boolean generatedByAgent,
        @NotNull StructuredTermsDTO terms,
        @NotBlank String rationaleSummary,
        @NotBlank String callerUtilityScore,
        boolean mandateCompliant,
        @NotBlank String createdAt) {}
