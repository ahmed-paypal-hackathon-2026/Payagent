package com.payagent.settlement.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.settlement.persistence.AuditEventEntity;
import com.payagent.settlement.persistence.AuditEventRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit events in the caller's transaction.
 *
 * <p>Financial reconciliation is not complete until an audit event exists.
 * If the audit write fails, the caller's transaction must roll back so the
 * webhook insert and the business write are discarded together; PayPal will
 * retry and the event is re-applied. This is deliberately coupled rather
 * than best-effort logging.
 */
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
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordEvent(UUID dealId, String eventType, Object payload) {
        String json;
        try {
            json = payload instanceof String s ? s : objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize audit payload for dealId=" + dealId + " eventType=" + eventType, e);
        }
        long maxSeq = auditEventRepository.findMaxSeqByDealId(dealId);
        long nextSeq = maxSeq + 1;
        AuditEventEntity entity = new AuditEventEntity(dealId, nextSeq, eventType, json);
        auditEventRepository.save(entity);
        log.info("Recorded audit event: dealId={}, seq={}, type={}", dealId, nextSeq, eventType);
    }
}
