package com.payagent.settlement.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {
    @Query("SELECT COALESCE(MAX(a.seq), 0) FROM AuditEventEntity a WHERE a.dealId = :dealId")
    long findMaxSeqByDealId(UUID dealId);
}
