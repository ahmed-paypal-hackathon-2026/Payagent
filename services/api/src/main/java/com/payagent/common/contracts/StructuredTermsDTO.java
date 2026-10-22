package com.payagent.common.contracts;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record StructuredTermsDTO(
        @NotBlank String title,
        @NotNull List<String> deliverables,
        @Min(0) long priceMinorUnits,
        @NotBlank String currency,
        @Min(1) int deliveryDays,
        @Min(0) int includedRevisions,
        @NotBlank String acceptanceCriteriaText) {}
