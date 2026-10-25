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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
            case "PAYMENT.CAPTURE.COMPLETED" -> handleCaptureCompleted(resourceId, resourceNode);
            case "PAYMENT.CAPTURE.DENIED" -> handleCaptureDenied(resourceId);
            case "INVOICING.INVOICE.PAID" -> handleInvoicePaid(resourceId);
            case "INVOICING.INVOICE.CANCELLED" -> handleInvoiceCancelled(resourceId);
            default -> log.info("Unhandled PayPal webhook event type: {}", eventType);
        }

        return true;
    }

    private void handleAuthorizationCreated(String authId, JsonNode node) {
        Optional<PaymentEntity> paymentOpt = paymentRepository.findByPaypalAuthorizationId(authId);
        if (paymentOpt.isEmpty() && node != null && node.has("parent_payment")) {
            String parent = node.get("parent_payment").asText();
            paymentOpt = paymentRepository.findByPaypalOrderId(parent);
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
        String authId = null;
        if (node != null && node.has("parent_payment")) {
            authId = node.get("parent_payment").asText();
        }

        Optional<PaymentEntity> paymentOpt = authId != null
                ? paymentRepository.findByPaypalAuthorizationId(authId)
                : Optional.empty();

        paymentOpt.ifPresent(payment -> {
            payment.setPaypalCaptureId(captureId);
            payment.setLifecycleStatus(PaymentLifecycleStatus.COMPLETED);
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);
            auditEventPort.recordEvent(payment.getDealId(), "WEBHOOK_RECONCILED_CAPTURED", Map.of("captureId", captureId));
        });
    }

    private void handleCaptureDenied(String captureId) {
        log.warn("Capture denied for id: {}", captureId);
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
            if (payment.getPaypalOrderId() != null && payment.getLifecycleStatus() == PaymentLifecycleStatus.UNKNOWN) {
                try {
                    var orderResp = orderService.getOrder(payment.getPaypalOrderId());
                    PaymentLifecycleStatus status = orderService.mapOrderStatus(orderResp.status());
                    if (status != PaymentLifecycleStatus.UNKNOWN) {
                        payment.setLifecycleStatus(status);
                        payment.setUpdatedAt(Instant.now());
                        paymentRepository.save(payment);
                    }
                } catch (Exception e) {
                    log.warn("Reconciliation GET failed for order {}: {}", payment.getPaypalOrderId(), e.getMessage());
                }
            }
        });
    }
}
