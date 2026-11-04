package com.payagent.disputes.model;

import com.payagent.common.contracts.DisputeStatus;
import java.time.Instant;
import java.util.UUID;

public record DisputeDTO(
        UUID id,
        UUID dealId,
        DisputeStatus status,
        String reason,
        String resolutionRequest,
        Instant openedAt,
        Instant resolvedAt
) {}
