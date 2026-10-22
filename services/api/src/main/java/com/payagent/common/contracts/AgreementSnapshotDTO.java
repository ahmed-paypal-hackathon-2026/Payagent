package com.payagent.common.contracts;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgreementSnapshotDTO(
        @NotBlank String agreementId,
        @NotBlank String dealId,
        @Min(1) int version,
        @NotNull StructuredTermsDTO canonicalTerms,
        @NotBlank String termsHashSha256,
        String buyerAcceptedAt,
        String sellerAcceptedAt) {}
