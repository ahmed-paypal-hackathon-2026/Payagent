package com.payagent.settlement.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayPalEventRepository extends JpaRepository<PayPalEventEntity, String> {
    boolean existsByEventId(String eventId);
}
