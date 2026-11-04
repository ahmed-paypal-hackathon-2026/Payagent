package com.payagent.settlement.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.settlement.persistence.AuditEventEntity;
import com.payagent.settlement.persistence.AuditEventRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnMissingBean(value = AuditEventPort.class, ignored = DefaultAuditEventPortAdapter.class)
public class DefaultAuditEventPortAdapter implements AuditEventPort {

    private static final Logger log = LoggerFactory.getLogger(DefaultAuditEventPortAdapter.class);

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    public DefaultAuditEventPortAdapter(AuditEventRepository auditEventRepository, ObjectMapper objectMapper) {
        this.auditEventRepository = auditEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void recordEvent(UUID dealId, String eventType, Object payload) {
        try {
            long maxSeq = auditEventRepository.findMaxSeqByDealId(dealId);
            long nextSeq = maxSeq + 1;
            String json = payload instanceof String s ? s : objectMapper.writeValueAsString(payload);
            AuditEventEntity entity = new AuditEventEntity(dealId, nextSeq, eventType, json);
            auditEventRepository.save(entity);
            log.info("Recorded audit event: dealId={}, seq={}, type={}", dealId, nextSeq, eventType);
        } catch (Exception e) {
            log.warn("Failed to persist audit event for deal {}: {}", dealId, e.getMessage());
        }
    }
}
