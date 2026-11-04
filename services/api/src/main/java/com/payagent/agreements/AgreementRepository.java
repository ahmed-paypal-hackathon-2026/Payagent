package com.payagent.agreements;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgreementRepository extends JpaRepository<Agreement, UUID> {
    Optional<Agreement> findByDealIdAndVersion(UUID dealId, Integer version);
    Optional<Agreement> findTopByDealIdOrderByVersionDesc(UUID dealId);
}
