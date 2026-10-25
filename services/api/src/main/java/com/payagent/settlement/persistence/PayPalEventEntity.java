package com.payagent.settlement.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "paypal_events")
public class PayPalEventEntity {

    @Id
    @Column(name = "event_id", length = 128, nullable = false)
    private String eventId;

    @Column(name = "event_type", length = 128, nullable = false)
    private String eventType;

    @Column(name = "resource_id", length = 128)
    private String resourceId;

    @Column(name = "payload_json", columnDefinition = "jsonb", nullable = false)
    private String payloadJson;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    public PayPalEventEntity() {}

    public PayPalEventEntity(String eventId, String eventType, String resourceId, String payloadJson) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.resourceId = resourceId;
        this.payloadJson = payloadJson;
        this.receivedAt = Instant.now();
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}
