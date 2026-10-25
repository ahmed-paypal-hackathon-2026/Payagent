package com.payagent.evidence.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EvidenceRepository extends JpaRepository<EvidenceEntity, UUID> {
    List<EvidenceEntity> findByDealIdOrderByVersionAsc(UUID dealId);
    Optional<EvidenceEntity> findByIdAndDealId(UUID id, UUID dealId);
    @Query("SELECT COALESCE(MAX(e.version), 0) FROM EvidenceEntity e WHERE e.dealId = :dealId")
    int findMaxVersionByDealId(UUID dealId);
}
