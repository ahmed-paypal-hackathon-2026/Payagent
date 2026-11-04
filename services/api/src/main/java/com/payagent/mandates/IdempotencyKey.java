package com.payagent.mandates;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persistent idempotency record for mutating endpoints.
 *
 * <p>Maps to the existing {@code idempotency_keys} table (V1__init.sql).
 * Primary key is ({@code idempotency_key}, {@code route}), so concurrent
 * requests with the same key+route cannot both insert — one wins and the
 * other must fall back to the cached response.
 */
@Entity
@IdClass(IdempotencyKeyId.class)
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "deal_id", columnDefinition = "UUID")
    private UUID dealId;

    @Id
    @Column(name = "route", nullable = false, length = 256)
    private String route;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected IdempotencyKey() {
    }

    public IdempotencyKey(String idempotencyKey, UUID dealId, String route,
                          String requestHash, Integer responseStatus, String responseBody) {
        this.idempotencyKey = idempotencyKey;
        this.dealId = dealId;
        this.route = route;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.createdAt = OffsetDateTime.now();
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getDealId() { return dealId; }
    public String getRoute() { return route; }
    public String getRequestHash() { return requestHash; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
