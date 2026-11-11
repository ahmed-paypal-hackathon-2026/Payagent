package com.payagent.settlement;

import com.payagent.settlement.persistence.PaymentEntity;
import com.payagent.settlement.persistence.PaymentRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the durable capture-attempt lifecycle.
 *
 * <p>A capture request is marked before the network call reaches PayPal. The
 * marker is written in its own {@code REQUIRES_NEW} transaction so it survives
 * rollback of the outer settlement transaction. An open attempt whose remote
 * outcome is unresolved must never cause another blind capture.
 */
@Service
public class CaptureAttemptService {

    private static final Logger log = LoggerFactory.getLogger(CaptureAttemptService.class);

    static final String OPEN = "OPEN";
    static final String PENDING_RECONCILIATION = "PENDING_RECONCILIATION";
    static final String RESOLVED = "RESOLVED";

    private final PaymentRepository paymentRepository;

    public CaptureAttemptService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCaptureAttempted(UUID paymentId, String captureRequestId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment not found for capture attempt: " + paymentId));
        payment.setCaptureAttemptedAt(Instant.now());
        payment.setCaptureRequestId(captureRequestId);
        payment.setCaptureResolution(OPEN);
        payment.setUpdatedAt(Instant.now());
        paymentRepository.save(payment);
        log.info("Recorded open capture attempt for payment {} with request id {}", paymentId, captureRequestId);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentEntity> findOpenAttempt(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .filter(p -> p.getCaptureAttemptedAt() != null
                        && Set.of(OPEN, PENDING_RECONCILIATION).contains(p.getCaptureResolution()));
    }
}
