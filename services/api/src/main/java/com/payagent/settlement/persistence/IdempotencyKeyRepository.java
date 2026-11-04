package com.payagent.settlement.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, IdempotencyKeyId> {
    Optional<IdempotencyKeyEntity> findByIdempotencyKeyAndRoute(String idempotencyKey, String route);
}
