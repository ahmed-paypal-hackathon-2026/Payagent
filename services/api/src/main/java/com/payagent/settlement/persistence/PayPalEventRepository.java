package com.payagent.settlement.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface PayPalEventRepository extends JpaRepository<PayPalEventEntity, String> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO paypal_events (event_id, event_type, resource_id, payload_json, received_at)
            VALUES (:eventId, :eventType, :resourceId, CAST(:payloadJson AS jsonb), NOW())
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId,
                       @Param("eventType") String eventType,
                       @Param("resourceId") String resourceId,
                       @Param("payloadJson") String payloadJson);
}
