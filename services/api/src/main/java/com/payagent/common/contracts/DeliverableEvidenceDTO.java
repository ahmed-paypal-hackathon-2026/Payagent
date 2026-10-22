package com.payagent.common.contracts;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeliverableEvidenceDTO(
        @NotBlank String evidenceId,
        @NotBlank String dealId,
        @NotNull PartyRole uploaderRole,
        @NotBlank String filename,
        @NotBlank String mimeType,
        @Min(0) long sizeBytes,
        @NotBlank String sha256Hash,
        @Min(1) int version,
        @NotBlank String downloadSignedUrl,
        @NotBlank String uploadedAt) {}
