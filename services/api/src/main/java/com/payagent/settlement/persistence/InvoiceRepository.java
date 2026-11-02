package com.payagent.settlement.persistence;

import com.payagent.common.contracts.InvoiceLifecycleStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<InvoiceEntity, UUID> {
    Optional<InvoiceEntity> findFirstByDealIdOrderByUpdatedAtDesc(UUID dealId);
    Optional<InvoiceEntity> findByPaypalInvoiceId(String paypalInvoiceId);
    List<InvoiceEntity> findByLifecycleStatusIn(Collection<InvoiceLifecycleStatus> statuses);
}
