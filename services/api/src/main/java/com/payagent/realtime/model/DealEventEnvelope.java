package com.payagent.realtime.model;

import java.time.Instant;
import java.util.UUID;

public record DealEventEnvelope(
        UUID dealId,
        long seq,
        String eventType,
        Object payload,
        Instant timestamp
) {}
