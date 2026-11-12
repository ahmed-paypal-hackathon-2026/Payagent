package com.payagent.agreements;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgreementRepository extends JpaRepository<Agreement, UUID> {
    Optional<Agreement> findByDealIdAndVersion(UUID dealId, Integer version);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Agreement a where a.dealId = :dealId and a.version = :version")
    Optional<Agreement> findByDealIdAndVersionForUpdate(@Param("dealId") UUID dealId, @Param("version") Integer version);

    Optional<Agreement> findTopByDealIdOrderByVersionDesc(UUID dealId);

    java.util.List<Agreement> findByTermsHashSha256(String termsHashSha256);
}
