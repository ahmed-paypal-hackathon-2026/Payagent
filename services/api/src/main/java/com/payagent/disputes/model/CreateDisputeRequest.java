package com.payagent.disputes.model;

import jakarta.validation.constraints.NotBlank;

public record CreateDisputeRequest(
        @NotBlank(message = "Dispute reason is required") String reason,
        String resolutionRequest
) {}
