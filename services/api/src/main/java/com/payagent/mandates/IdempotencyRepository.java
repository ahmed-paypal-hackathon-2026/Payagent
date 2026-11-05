package com.payagent.mandates;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyKey, IdempotencyKeyId> {

    Optional<IdempotencyKey> findByIdempotencyKeyAndRoute(String idempotencyKey, String route);
}
