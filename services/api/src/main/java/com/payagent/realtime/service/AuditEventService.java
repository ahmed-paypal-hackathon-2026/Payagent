package com.payagent.realtime.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.common.contracts.PartyRole;
import com.payagent.realtime.model.AuditEventDTO;
import com.payagent.realtime.model.DealEventEnvelope;
import com.payagent.settlement.persistence.AuditEventEntity;
import com.payagent.settlement.persistence.AuditEventRepository;
import com.payagent.settlement.port.AuditEventPort;
import com.payagent.settlement.port.DealPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class AuditEventService implements AuditEventPort {

    private static final Logger log = LoggerFactory.getLogger(AuditEventService.class);

    private final AuditEventRepository auditEventRepository;
    private final DealPort dealPort;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DealWebSocketBroker webSocketBroker;

    private final Map<UUID, Object> dealSequenceLocks = new ConcurrentHashMap<>();

    public AuditEventService(
            AuditEventRepository auditEventRepository,
            DealPort dealPort,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            DealWebSocketBroker webSocketBroker) {
        this.auditEventRepository = auditEventRepository;
        this.dealPort = dealPort;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.webSocketBroker = webSocketBroker;
    }

    @Override
    @Transactional
    public void recordEvent(UUID dealId, String eventType, Object payload) {
        Object lock = dealSequenceLocks.computeIfAbsent(dealId, k -> new Object());
        synchronized (lock) {
            try {
                long maxSeq = auditEventRepository.findMaxSeqByDealId(dealId);
                long nextSeq = maxSeq + 1;

                String jsonPayload = payload instanceof String s ? s : objectMapper.writeValueAsString(payload);
                AuditEventEntity entity = new AuditEventEntity(dealId, nextSeq, eventType, jsonPayload);
                auditEventRepository.save(entity);

                log.info("Recorded audit event for deal {}: seq={}, eventType={}", dealId, nextSeq, eventType);

                // Broadcast over WebSocket broker
                Object parsedPayload = payload instanceof String
                        ? tryParseJson((String) payload)
                        : payload;
                DealEventEnvelope envelope = new DealEventEnvelope(
                        dealId,
                        nextSeq,
                        eventType,
                        sanitizeForBroadcast(parsedPayload),
                        Instant.now()
                );
                webSocketBroker.broadcastDealEvent(dealId, envelope);

            } catch (Exception e) {
                log.error("Failed to record audit event for deal {}: {}", dealId, e.getMessage(), e);
                throw new IllegalStateException("Failed to record audit event: " + e.getMessage(), e);
            }
        }
    }

    public List<AuditEventDTO> getTimeline(UUID dealId, long afterSeq, String callerEmail) {
        dealPort.assertParty(dealId, callerEmail);
        PartyRole callerRole = resolveCallerRole(dealId, callerEmail);

        String sql = """
                SELECT id, deal_id, seq, event_type, payload_json, created_at
                FROM audit_events
                WHERE deal_id = ? AND seq > ?
                ORDER BY seq ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRowToAuditEventDTO(rs, callerRole), dealId, afterSeq);
    }

    public void replayForSubscriber(UUID dealId, long afterSeq, String callerEmail) {
        List<AuditEventDTO> missedEvents = getTimeline(dealId, afterSeq, callerEmail);
        for (AuditEventDTO event : missedEvents) {
            DealEventEnvelope envelope = new DealEventEnvelope(
                    event.dealId(),
                    event.seq(),
                    event.eventType(),
                    event.payload(),
                    event.createdAt()
            );
            webSocketBroker.sendToUser(callerEmail, "/queue/deal-replay", envelope);
        }
    }

    private AuditEventDTO mapRowToAuditEventDTO(ResultSet rs, PartyRole callerRole) throws SQLException {
        long id = rs.getLong("id");
        UUID dealId = (UUID) rs.getObject("deal_id");
        long seq = rs.getLong("seq");
        String eventType = rs.getString("event_type");
        String payloadJson;
        Object rawJson = rs.getObject("payload_json");
        if (rawJson instanceof byte[] bytes) {
            payloadJson = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } else if (rawJson != null) {
            payloadJson = rawJson.toString();
        } else {
            payloadJson = rs.getString("payload_json");
        }

        Instant createdAt;
        Object createdAtObj = rs.getObject("created_at");
        if (createdAtObj instanceof OffsetDateTime odt) {
            createdAt = odt.toInstant();
        } else if (createdAtObj instanceof java.sql.Timestamp ts) {
            createdAt = ts.toInstant();
        } else {
            createdAt = Instant.now();
        }

        Object parsedPayload = tryParseJson(payloadJson);
        Object filteredPayload = sanitizeForRole(parsedPayload, callerRole);

        return new AuditEventDTO(id, dealId, seq, eventType, filteredPayload, createdAt);
    }

    private PartyRole resolveCallerRole(UUID dealId, String callerEmail) {
        if (callerEmail == null || callerEmail.isBlank()) {
            return PartyRole.BUYER;
        }
        String buyerEmail = dealPort.getBuyerEmail(dealId);
        if (callerEmail.trim().equalsIgnoreCase(buyerEmail)) {
            return PartyRole.BUYER;
        }
        String sellerEmail = dealPort.getSellerEmail(dealId);
        if (sellerEmail != null && callerEmail.trim().equalsIgnoreCase(sellerEmail)) {
            return PartyRole.SELLER;
        }
        return PartyRole.BUYER;
    }

    /**
     * Sanitizes payloads to protect private deal limits (buyer max budget, seller min price, target price).
     * Counterparty must never see private limits.
     */
    @SuppressWarnings("unchecked")
    private Object sanitizeForRole(Object payload, PartyRole callerRole) {
        if (!(payload instanceof Map<?, ?> rawMap)) {
            return payload;
        }

        Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) rawMap);

        // If payload is a Mandate or contains mandate information
        String partyRole = (String) map.get("partyRole");
        if (partyRole != null) {
            boolean isCounterparty = !partyRole.equalsIgnoreCase(callerRole.name());
            if (isCounterparty) {
                // Mask counterparty private limits
                map.remove("limitPriceMinorUnits");
                map.remove("targetPriceMinorUnits");
                map.remove("limitDeliveryDays");
                map.remove("targetDeliveryDays");
                map.remove("limitRevisions");
                map.remove("targetRevisions");
                map.remove("forbiddenTerms");
                map.put("privateLimitsMasked", true);
            }
        }

        // Also check nested maps
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?>) {
                entry.setValue(sanitizeForRole(entry.getValue(), callerRole));
            }
        }

        return map;
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeForBroadcast(Object payload) {
        if (!(payload instanceof Map<?, ?> rawMap)) {
            return payload;
        }
        Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) rawMap);
        // On public deal channel, never emit raw unmasked private mandate limits
        if (map.containsKey("limitPriceMinorUnits") || map.containsKey("targetPriceMinorUnits")) {
            map.remove("limitPriceMinorUnits");
            map.remove("targetPriceMinorUnits");
            map.remove("forbiddenTerms");
            map.put("privateLimitsMasked", true);
        }
        return map;
    }

    private Object tryParseJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        String cleanJson = json.trim();
        if (cleanJson.startsWith("JSON '") && cleanJson.endsWith("'")) {
            cleanJson = cleanJson.substring(6, cleanJson.length() - 1);
        }
        try {
            Object parsed = objectMapper.readValue(cleanJson, Object.class);
            if (parsed instanceof String nested && (nested.startsWith("{") || nested.startsWith("["))) {
                parsed = objectMapper.readValue(nested, Object.class);
            }
            return parsed;
        } catch (Exception e) {
            log.warn("Could not parse payload as JSON object: '{}', returning raw string: {}", cleanJson, e.getMessage());
            return cleanJson;
        }
    }
}
