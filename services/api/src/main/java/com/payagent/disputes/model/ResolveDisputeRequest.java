package com.payagent.disputes.model;

import com.payagent.common.contracts.DisputeStatus;
import jakarta.validation.constraints.NotNull;

public record ResolveDisputeRequest(
        @NotNull(message = "Resolution status is required") DisputeStatus resolution,
        String resolutionNotes
) {}
