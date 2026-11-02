package com.payagent.realtime.model;

import java.time.Instant;
import java.util.UUID;

public record AuditEventDTO(
        Long id,
        UUID dealId,
        long seq,
        String eventType,
        Object payload,
        Instant createdAt
) {}
