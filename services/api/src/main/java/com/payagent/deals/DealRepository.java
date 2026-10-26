package com.payagent.deals;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DealRepository extends JpaRepository<Deal, UUID> {
    Optional<Deal> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Deal d where d.id = :id")
    Optional<Deal> findByIdForUpdate(@Param("id") UUID id);
}
