package com.payagent.settlement.persistence;

import com.payagent.common.contracts.PaymentLifecycleStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findFirstByDealIdOrderByUpdatedAtDesc(UUID dealId);
    Optional<PaymentEntity> findByPaypalOrderId(String paypalOrderId);
    Optional<PaymentEntity> findByPaypalAuthorizationId(String paypalAuthorizationId);
    Optional<PaymentEntity> findByPaypalCaptureId(String paypalCaptureId);
    List<PaymentEntity> findByLifecycleStatusIn(Collection<PaymentLifecycleStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentEntity p WHERE p.captureAttemptedAt IS NOT NULL AND p.captureResolution IN :resolutions AND p.lifecycleStatus IN :statuses ORDER BY p.captureAttemptedAt ASC")
    List<PaymentEntity> findOpenCaptureAttemptsForUpdate(
            @Param("resolutions") Collection<String> resolutions,
            @Param("statuses") Collection<PaymentLifecycleStatus> statuses);

    /**
     * Captures that were initiated locally but not yet persisted as completed.
     * PESSIMISTIC_WRITE gives row-level locking so concurrent reconciliation
     * workers skip payments already being processed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentEntity p WHERE p.captureAttemptedAt IS NOT NULL AND p.lifecycleStatus IN :statuses")
    List<PaymentEntity> findByCaptureAttemptedAtIsNotNullAndLifecycleStatusInForUpdate(
            @Param("statuses") Collection<PaymentLifecycleStatus> statuses);
}
