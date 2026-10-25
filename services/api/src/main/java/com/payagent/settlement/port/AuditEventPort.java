package com.payagent.settlement.port;

import java.util.UUID;

public interface AuditEventPort {
    void recordEvent(UUID dealId, String eventType, Object payload);
}
