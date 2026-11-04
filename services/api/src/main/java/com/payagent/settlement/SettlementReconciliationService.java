package com.payagent.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.payagent.common.contracts.InvoiceLifecycleStatus;
import com.payagent.common.contracts.PaymentLifecycleStatus;
import com.payagent.paypal.PayPalInvoiceService;
import com.payagent.paypal.PayPalOrderService;
import com.payagent.settlement.persistence.InvoiceEntity;
import com.payagent.settlement.persistence.InvoiceRepository;
import com.payagent.settlement.persistence.PayPalEventEntity;
import com.payagent.settlement.persistence.PayPalEventRepository;
import com.payagent.settlement.persistence.PaymentEntity;
import com.payagent.settlement.persistence.PaymentRepository;
import com.payagent.settlement.port.AuditEventPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(SettlementReconciliationService.class);

    private final PayPalEventRepository payPalEventRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final PayPalInvoiceService invoiceService;
    private final PayPalOrderService orderService;
    private final AuditEventPort auditEventPort;

    public SettlementReconciliationService(
            PayPalEventRepository payPalEventRepository,
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            PayPalInvoiceService invoiceService,
            PayPalOrderService orderService,
            AuditEventPort auditEventPort) {
        this.payPalEventRepository = payPalEventRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceService = invoiceService;
        this.orderService = orderService;
        this.auditEventPort = auditEventPort;
    }

    @Transactional
    public boolean processWebhookEvent(String eventId, String eventType, String resourceId, JsonNode resourceNode, String rawJson) {
        if (payPalEventRepository.existsByEventId(eventId)) {
            log.info("Duplicate PayPal webhook event {} ignored.", eventId);
            return false;
        }

        // Deduplication storage
        PayPalEventEntity eventEntity = new PayPalEventEntity(eventId, eventType, resourceId, rawJson);
        payPalEventRepository.save(eventEntity);
        log.info("Persisted PayPal webhook event: id={}, type={}, resource={}", eventId, eventType, resourceId);

        // State machine reconciliation
        switch (eventType) {
            case "PAYMENT.AUTHORIZATION.CREATED" -> handleAuthorizationCreated(resourceId, resourceNode);
            case "PAYMENT.AUTHORIZATION.VOIDED" -> handleAuthorizationVoided(resourceId);
            case "PAYMENT.AUTHORIZATION.CAPTURED" -> handleCaptureCompleted(resourceId, resourceNode);
            case "PAYMENT.CAPTURE.COMPLETED" -> handleCaptureCompleted(resourceId, resourceNode);
            case "PAYMENT.CAPTURE.PENDING" -> log.info("Capture pending for resource {} on deal reconciliation", resourceId);
            case "PAYMENT.CAPTURE.DENIED" -> handleCaptureDenied(resourceId, resourceNode);
            case "INVOICING.INVOICE.PAID" -> handleInvoicePaid(resourceId);
            case "INVOICING.INVOICE.CANCELLED" -> handleInvoiceCancelled(resourceId);
            default -> log.info("Unhandled PayPal webhook event type: {}", eventType);
        }

        return true;
    }

    private void handleAuthorizationCreated(String authId, JsonNode node) {
        Optional<PaymentEntity> paymentOpt = paymentRepository.findByPaypalAuthorizationId(authId);
        if (paymentOpt.isEmpty()) {
            String orderId = relatedId(node, "order_id", "parent_payment");
            if (orderId != null) {
                paymentOpt = paymentRepository.findByPaypalOrderId(orderId);
            }
        }

        paymentOpt.ifPresent(payment -> {
            payment.setPaypalAuthorizationId(authId);
            payment.setLifecycleStatus(PaymentLifecycleStatus.AUTHORIZED);
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);
            auditEventPort.recordEvent(payment.getDealId(), "WEBHOOK_RECONCILED_AUTHORIZED", Map.of("authId", authId));
        });
    }

    private void handleAuthorizationVoided(String authId) {
        paymentRepository.findByPaypalAuthorizationId(authId).ifPresent(payment -> {
            payment.setLifecycleStatus(PaymentLifecycleStatus.VOIDED);
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);
            auditEventPort.recordEvent(payment.getDealId(), "WEBHOOK_RECONCILED_VOIDED", Map.of("authId", authId));
        });
    }

    private void handleCaptureCompleted(String captureId, JsonNode node) {
        resolvePayment(node, captureId).ifPresent(payment -> {
            payment.setPaypalCaptureId(captureId);
            payment.setLifecycleStatus(PaymentLifecycleStatus.COMPLETED);
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);

            invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(payment.getDealId()).ifPresent(invoice -> {
                if (invoice.getLifecycleStatus() != InvoiceLifecycleStatus.PAID
                        && invoice.getLifecycleStatus() != InvoiceLifecycleStatus.CANCELLED) {
                    invoice.setLifecycleStatus(InvoiceLifecycleStatus.PAID);
                    invoice.setUpdatedAt(Instant.now());
                    invoiceRepository.save(invoice);
                    auditEventPort.recordEvent(payment.getDealId(), "WEBHOOK_RECONCILED_INVOICE_PAID",
                            Map.of("invoiceId", String.valueOf(invoice.getPaypalInvoiceId())));
                }
            });

            auditEventPort.recordEvent(payment.getDealId(), "WEBHOOK_RECONCILED_CAPTURED", Map.of("captureId", captureId));
        });
    }

    private void handleCaptureDenied(String captureId, JsonNode node) {
        resolvePayment(node, captureId).ifPresentOrElse(payment -> {
            payment.setPaypalCaptureId(captureId);
            payment.setLifecycleStatus(PaymentLifecycleStatus.DECLINED);
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);
            auditEventPort.recordEvent(payment.getDealId(), "WEBHOOK_RECONCILED_CAPTURE_DENIED", Map.of("captureId", captureId));
        }, () -> log.warn("Capture denied for unknown capture id: {}", captureId));
    }

    private Optional<PaymentEntity> resolvePayment(JsonNode node, String captureId) {
        String authId = relatedId(node, "authorization_id", "parent_payment");
        if (authId != null) {
            Optional<PaymentEntity> byAuth = paymentRepository.findByPaypalAuthorizationId(authId);
            if (byAuth.isPresent()) {
                return byAuth;
            }
        }

        String orderId = relatedId(node, "order_id", "order_id");
        if (orderId != null) {
            Optional<PaymentEntity> byOrder = paymentRepository.findByPaypalOrderId(orderId);
            if (byOrder.isPresent()) {
                return byOrder;
            }
        }

        return captureId != null ? paymentRepository.findByPaypalCaptureId(captureId) : Optional.empty();
    }

    private void handleInvoicePaid(String invoiceId) {
        invoiceRepository.findByPaypalInvoiceId(invoiceId).ifPresent(invoice -> {
            invoice.setLifecycleStatus(InvoiceLifecycleStatus.PAID);
            invoice.setUpdatedAt(Instant.now());
            invoiceRepository.save(invoice);
            auditEventPort.recordEvent(invoice.getDealId(), "WEBHOOK_RECONCILED_INVOICE_PAID", Map.of("invoiceId", invoiceId));
        });
    }

    private void handleInvoiceCancelled(String invoiceId) {
        invoiceRepository.findByPaypalInvoiceId(invoiceId).ifPresent(invoice -> {
            invoice.setLifecycleStatus(InvoiceLifecycleStatus.CANCELLED);
            invoice.setUpdatedAt(Instant.now());
            invoiceRepository.save(invoice);
            auditEventPort.recordEvent(invoice.getDealId(), "WEBHOOK_RECONCILED_INVOICE_CANCELLED", Map.of("invoiceId", invoiceId));
        });
    }

    private String relatedId(JsonNode node, String relatedIdName, String legacyFieldName) {
        if (node == null) {
            return null;
        }
        JsonNode relatedIds = node.path("supplementary_data").path("related_ids");
        if (relatedIds.hasNonNull(relatedIdName)) {
            return relatedIds.get(relatedIdName).asText();
        }
        if (node.hasNonNull(legacyFieldName)) {
            return node.get(legacyFieldName).asText();
        }
        return null;
    }

    @Transactional
    public void reconcileDeal(UUID dealId) {
        invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId).ifPresent(invoice -> {
            if (invoice.getPaypalInvoiceId() != null) {
                try {
                    var resp = invoiceService.getInvoice(invoice.getPaypalInvoiceId());
                    InvoiceLifecycleStatus status = invoiceService.mapToLifecycleStatus(resp.status());
                    if (status != InvoiceLifecycleStatus.UNKNOWN && status != invoice.getLifecycleStatus()) {
                        invoice.setLifecycleStatus(status);
                        invoice.setUpdatedAt(Instant.now());
                        invoiceRepository.save(invoice);
                    }
                } catch (Exception e) {
                    log.warn("Reconciliation GET failed for invoice {}: {}", invoice.getPaypalInvoiceId(), e.getMessage());
                }
            }
        });

        paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId).ifPresent(payment -> {
            if (payment.getLifecycleStatus() != PaymentLifecycleStatus.UNKNOWN
                    && payment.getLifecycleStatus() != PaymentLifecycleStatus.CAPTURE_PENDING) {
                return;
            }

            try {
                PaymentLifecycleStatus status = null;

                if (payment.getPaypalCaptureId() != null) {
                    var capture = orderService.getCapture(payment.getPaypalCaptureId());
                    status = orderService.mapCaptureStatus(capture.status());
                } else if (payment.getPaypalAuthorizationId() != null) {
                    var authorization = orderService.getAuthorization(payment.getPaypalAuthorizationId());
                    status = orderService.mapAuthorizationStatus(authorization.status());
                } else if (payment.getPaypalOrderId() != null) {
                    var orderResp = orderService.getOrder(payment.getPaypalOrderId());
                    status = orderService.mapOrderStatus(orderResp.status());
                }

                if (status != null && status != PaymentLifecycleStatus.UNKNOWN) {
                    payment.setLifecycleStatus(status);
                    payment.setUpdatedAt(Instant.now());
                    paymentRepository.save(payment);
                    auditEventPort.recordEvent(dealId, "RECONCILED_PAYMENT_STATUS",
                            Map.of("status", status.name()));
                }
            } catch (Exception e) {
                log.warn("Reconciliation GET failed for payment on deal {}: {}", dealId, e.getMessage());
            }
        });
    }

    /**
     * Recovery job for webhooks that were missed or arrived in an UNKNOWN / PENDING state.
     * Scheduled reconciliation is required by the UNKNOWN-state invariant.
     */
    @Scheduled(
            initialDelayString = "${payagent.paypal.reconciliation.initial-delay-ms:60000}",
            fixedDelayString = "${payagent.paypal.reconciliation.interval-ms:300000}"
    )
    public void reconcileStaleSettlements() {
        List<PaymentEntity> payments = paymentRepository.findByLifecycleStatusIn(
                Set.of(PaymentLifecycleStatus.UNKNOWN, PaymentLifecycleStatus.CAPTURE_PENDING));
        for (PaymentEntity payment : payments) {
            try {
                reconcileDeal(payment.getDealId());
            } catch (Exception e) {
                log.warn("Scheduled reconciliation failed for deal {}: {}", payment.getDealId(), e.getMessage());
            }
        }

        List<InvoiceEntity> invoices = invoiceRepository.findByLifecycleStatusIn(
                Set.of(InvoiceLifecycleStatus.UNKNOWN));
        for (InvoiceEntity invoice : invoices) {
            try {
                reconcileDeal(invoice.getDealId());
            } catch (Exception e) {
                log.warn("Scheduled reconciliation failed for invoice on deal {}: {}", invoice.getDealId(), e.getMessage());
            }
        }
    }
}
