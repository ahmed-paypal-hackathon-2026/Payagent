package com.payagent.disputes.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DisputeRepository extends JpaRepository<DisputeEntity, UUID> {
    List<DisputeEntity> findByDealIdOrderByOpenedAtDesc(UUID dealId);
    Optional<DisputeEntity> findByIdAndDealId(UUID id, UUID dealId);
}
