package com.payagent.settlement.persistence;

import com.payagent.common.contracts.PaymentLifecycleStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findFirstByDealIdOrderByUpdatedAtDesc(UUID dealId);
    Optional<PaymentEntity> findByPaypalOrderId(String paypalOrderId);
    Optional<PaymentEntity> findByPaypalAuthorizationId(String paypalAuthorizationId);
    Optional<PaymentEntity> findByPaypalCaptureId(String paypalCaptureId);
    List<PaymentEntity> findByLifecycleStatusIn(Collection<PaymentLifecycleStatus> statuses);
}
