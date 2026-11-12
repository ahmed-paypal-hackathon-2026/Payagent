package com.payagent.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.InvoiceLifecycleStatus;
import com.payagent.common.contracts.PaymentLifecycleStatus;
import com.payagent.paypal.PayPalInvoiceService;
import com.payagent.paypal.PayPalOrderService;
import com.payagent.agreements.Agreement;
import com.payagent.agreements.AgreementRepository;
import com.payagent.settlement.persistence.InvoiceEntity;
import com.payagent.settlement.persistence.InvoiceRepository;
import com.payagent.settlement.persistence.PayPalEventEntity;
import com.payagent.settlement.persistence.PayPalEventRepository;
import com.payagent.settlement.persistence.PaymentEntity;
import com.payagent.settlement.persistence.PaymentRepository;
import com.payagent.settlement.port.AuditEventPort;
import com.payagent.settlement.port.DealPort;
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

import com.payagent.agreements.Agreement;
import com.payagent.agreements.AgreementRepository;

@Service
public class SettlementReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(SettlementReconciliationService.class);

    private final PayPalEventRepository payPalEventRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final PayPalInvoiceService invoiceService;
    private final PayPalOrderService orderService;
    private final AuditEventPort auditEventPort;
    private final DealPort dealPort;
    private final AgreementRepository agreementRepository;

    public SettlementReconciliationService(
            PayPalEventRepository payPalEventRepository,
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            PayPalInvoiceService invoiceService,
            PayPalOrderService orderService,
            AuditEventPort auditEventPort,
            DealPort dealPort,
            AgreementRepository agreementRepository) {
        this.payPalEventRepository = payPalEventRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceService = invoiceService;
        this.orderService = orderService;
        this.auditEventPort = auditEventPort;
        this.dealPort = dealPort;
        this.agreementRepository = agreementRepository;
    }

    @Transactional
    public WebhookProcessingResult processWebhookEvent(String eventId, String eventType, String resourceId, JsonNode resourceNode, String rawJson) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }

        int inserted = payPalEventRepository.insertIfAbsent(eventId, eventType, resourceId, rawJson);
        if (inserted == 0) {
            log.info("Duplicate PayPal webhook event {} ignored (concurrent or repeated delivery).", eventId);
            return WebhookProcessingResult.DUPLICATE;
        }
        if (inserted != 1) {
            throw new IllegalStateException("Unexpected paypal_events insert result for event " + eventId + ": " + inserted);
        }
        log.info("Persisted PayPal webhook event: id={}, type={}, resource={}", eventId, eventType, resourceId);

        // PayPal nests invoice payloads under resource.invoice (verified against real delivery:
        // INVOICING.INVOICE.CREATED), rather than directly under resource. Normalise for all INVOICING.* events.
        if (eventType != null && eventType.startsWith("INVOICING.") && resourceNode != null && resourceNode.hasNonNull("invoice")) {
            resourceNode = resourceNode.path("invoice");
            resourceId = resourceNode.path("id").asText(null);
        }

        // State machine reconciliation with explicit transition policy.
        return switch (eventType) {
            case "PAYMENT.AUTHORIZATION.CREATED" ->
                    handleAuthorizationCreated(resourceId, resourceNode)
                            ? WebhookProcessingResult.PROCESSED
                            : WebhookProcessingResult.ACKNOWLEDGED_NO_BUSINESS_MUTATION;
            case "PAYMENT.AUTHORIZATION.VOIDED" ->
                    handleAuthorizationVoided(resourceId)
                            ? WebhookProcessingResult.PROCESSED
                            : WebhookProcessingResult.ACKNOWLEDGED_NO_BUSINESS_MUTATION;
            case "PAYMENT.AUTHORIZATION.CAPTURED", "PAYMENT.CAPTURE.COMPLETED" ->
                    handleCaptureCompleted(resourceId, resourceNode)
                            ? WebhookProcessingResult.PROCESSED
                            : WebhookProcessingResult.ACKNOWLEDGED_NO_BUSINESS_MUTATION;
            case "PAYMENT.CAPTURE.PENDING" ->
                    handleCapturePending(resourceId, resourceNode)
                            ? WebhookProcessingResult.PROCESSED
                            : WebhookProcessingResult.ACKNOWLEDGED_NO_BUSINESS_MUTATION;
            case "PAYMENT.CAPTURE.DENIED" ->
                    handleCaptureDenied(resourceId, resourceNode)
                            ? WebhookProcessingResult.PROCESSED
                            : WebhookProcessingResult.ACKNOWLEDGED_NO_BUSINESS_MUTATION;
            case "INVOICING.INVOICE.PAID" ->
                    handleInvoicePaid(resourceId)
                            ? WebhookProcessingResult.PROCESSED
                            : WebhookProcessingResult.ACKNOWLEDGED_NO_BUSINESS_MUTATION;
            case "INVOICING.INVOICE.CREATED" ->
                    handleInvoiceCreated(resourceId, resourceNode)
                            ? WebhookProcessingResult.PROCESSED
                            : WebhookProcessingResult.ACKNOWLEDGED_NO_BUSINESS_MUTATION;
            case "INVOICING.INVOICE.SENT" ->
                    handleInvoiceSent(resourceId, resourceNode)
                            ? WebhookProcessingResult.PROCESSED
                            : WebhookProcessingResult.ACKNOWLEDGED_NO_BUSINESS_MUTATION;
            case "INVOICING.INVOICE.CANCELLED" ->
                    handleInvoiceCancelled(resourceId)
                            ? WebhookProcessingResult.PROCESSED
                            : WebhookProcessingResult.ACKNOWLEDGED_NO_BUSINESS_MUTATION;
            default -> {
                log.info("Unsupported/ignored PayPal webhook event type: {} (eventId={})", eventType, eventId);
                yield WebhookProcessingResult.IGNORED_UNSUPPORTED;
            }
        };
    }

    public enum WebhookProcessingResult {
        PROCESSED,
        DUPLICATE,
        IGNORED_UNSUPPORTED,
        ACKNOWLEDGED_NO_BUSINESS_MUTATION
    }

    private boolean handleAuthorizationCreated(String authId, JsonNode node) {
        Optional<PaymentEntity> paymentOpt = paymentRepository.findByPaypalAuthorizationId(authId);
        if (paymentOpt.isEmpty()) {
            String orderId = relatedId(node, "order_id", "parent_payment");
            if (orderId != null) {
                paymentOpt = paymentRepository.findByPaypalOrderId(orderId);
            }
        }

        if (paymentOpt.isEmpty()) {
            log.info("Authorization-created ignored: no local payment for authId={} (resource may belong to another flow)", authId);
            return false;
        }
        PaymentEntity payment = paymentOpt.get();
        var decision = SettlementStateGuard.canApplyAuthorizationCreated(payment.getLifecycleStatus());
        switch (decision) {
            case NO_CHANGE -> {
                log.info("Authorization-created already reflected locally for authId={}", authId);
                return false;
            }
            case CONFLICT -> {
                SettlementStateGuard.logConflict("AUTHORIZATION_CREATED", authId, payment.getLifecycleStatus(), PaymentLifecycleStatus.AUTHORIZED);
                auditConflict(payment.getDealId(), "WEBHOOK_CONFLICT_AUTHORIZATION_CREATED", authId, payment.getLifecycleStatus(), PaymentLifecycleStatus.AUTHORIZED);
                return false;
            }
            case APPLY -> {
                payment.setPaypalAuthorizationId(authId);
                payment.setLifecycleStatus(PaymentLifecycleStatus.AUTHORIZED);
                payment.setUpdatedAt(Instant.now());
                paymentRepository.save(payment);
                auditEventPort.recordEvent(payment.getDealId(), "WEBHOOK_RECONCILED_AUTHORIZED", Map.of("authId", authId));
                try {
                    if (dealPort.getDealStatus(payment.getDealId()) == DealStatus.SETTLEMENT_PENDING) {
                        transitionQuietly(payment.getDealId(), DealStatus.IN_PROGRESS);
                    }
                } catch (RuntimeException e) {
                    log.warn("Could not advance deal {} after authorization reconciliation: {}", payment.getDealId(), e.getMessage());
                }
                return true;
            }
            default -> throw new IllegalStateException("Unexpected guard result " + decision);
        }
    }

    private boolean handleAuthorizationVoided(String authId) {
        Optional<PaymentEntity> paymentOpt = paymentRepository.findByPaypalAuthorizationId(authId);
        if (paymentOpt.isEmpty()) {
            log.info("Authorization-voided ignored: no local payment for authId={}", authId);
            return false;
        }
        PaymentEntity payment = paymentOpt.get();
        var decision = SettlementStateGuard.canApplyAuthorizationVoided(payment.getLifecycleStatus());
        switch (decision) {
            case NO_CHANGE -> {
                log.info("Authorization-voided already reflected locally for authId={}", authId);
                return false;
            }
            case CONFLICT -> {
                SettlementStateGuard.logConflict("AUTHORIZATION_VOIDED", authId, payment.getLifecycleStatus(), PaymentLifecycleStatus.VOIDED);
                auditConflict(payment.getDealId(), "WEBHOOK_CONFLICT_AUTHORIZATION_VOIDED", authId, payment.getLifecycleStatus(), PaymentLifecycleStatus.VOIDED);
                return false;
            }
            case APPLY -> {
                payment.setLifecycleStatus(PaymentLifecycleStatus.VOIDED);
                payment.setCaptureAttemptedAt(null);
                payment.setUpdatedAt(Instant.now());
                paymentRepository.save(payment);
                auditEventPort.recordEvent(payment.getDealId(), "WEBHOOK_RECONCILED_VOIDED", Map.of("authId", authId));
                return true;
            }
            default -> throw new IllegalStateException("Unexpected guard result " + decision);
        }
    }

    private boolean handleCapturePending(String resourceId, JsonNode node) {
        Optional<PaymentEntity> paymentOpt = resolvePayment(node, null);
        if (paymentOpt.isEmpty()) {
            log.info("Capture-pending ignored: no local payment for resource {}", resourceId);
            return false;
        }
        PaymentEntity payment = paymentOpt.get();
        var decision = SettlementStateGuard.canApplyCapturePending(payment.getLifecycleStatus());
        switch (decision) {
            case NO_CHANGE -> {
                log.info("Capture-pending already reflected locally for payment {}", payment.getId());
                return false;
            }
            case CONFLICT -> {
                SettlementStateGuard.logConflict("CAPTURE_PENDING", resourceId, payment.getLifecycleStatus(), PaymentLifecycleStatus.CAPTURE_PENDING);
                auditConflict(payment.getDealId(), "WEBHOOK_CONFLICT_CAPTURE_PENDING", resourceId, payment.getLifecycleStatus(), PaymentLifecycleStatus.CAPTURE_PENDING);
                return false;
            }
            case APPLY -> {
                payment.setPaypalCaptureId(resourceId);
                payment.setLifecycleStatus(PaymentLifecycleStatus.CAPTURE_PENDING);
                payment.setUpdatedAt(Instant.now());
                paymentRepository.save(payment);
                auditEventPort.recordEvent(payment.getDealId(), "WEBHOOK_CAPTURE_PENDING", Map.of("resourceId", resourceId));
                return true;
            }
            default -> throw new IllegalStateException("Unexpected guard result " + decision);
        }
    }

    private boolean handleCaptureCompleted(String captureId, JsonNode node) {
        Optional<PaymentEntity> paymentOpt = resolvePayment(node, captureId);
        if (paymentOpt.isEmpty()) {
            log.info("Capture-completed ignored: no local payment for captureId={}", captureId);
            return false;
        }
        PaymentEntity payment = paymentOpt.get();
        var decision = SettlementStateGuard.canApplyCaptureCompleted(payment.getLifecycleStatus());
        switch (decision) {
            case NO_CHANGE -> {
                log.info("Capture-completed already reflected locally for payment {}", payment.getId());
                return false;
            }
            case CONFLICT -> {
                SettlementStateGuard.logConflict("CAPTURE_COMPLETED", captureId, payment.getLifecycleStatus(), PaymentLifecycleStatus.COMPLETED);
                auditConflict(payment.getDealId(), "WEBHOOK_CONFLICT_CAPTURE_COMPLETED", captureId, payment.getLifecycleStatus(), PaymentLifecycleStatus.COMPLETED);
                return false;
            }
            case APPLY -> {
                payment.setPaypalCaptureId(captureId);
                payment.setLifecycleStatus(PaymentLifecycleStatus.COMPLETED);
                payment.setCaptureAttemptedAt(null);
                payment.setUpdatedAt(Instant.now());
                paymentRepository.save(payment);

                markInvoicePaidIfEligible(payment);
                finalizeDealAfterCapture(payment.getDealId());

                auditEventPort.recordEvent(payment.getDealId(), "WEBHOOK_RECONCILED_CAPTURED", Map.of("captureId", captureId));
                return true;
            }
            default -> throw new IllegalStateException("Unexpected guard result " + decision);
        }
    }

    private boolean handleCaptureDenied(String captureId, JsonNode node) {
        Optional<PaymentEntity> paymentOpt = resolvePayment(node, captureId);
        if (paymentOpt.isEmpty()) {
            log.warn("Capture denied for unknown capture id: {}", captureId);
            return false;
        }
        PaymentEntity payment = paymentOpt.get();
        var decision = SettlementStateGuard.canApplyCaptureDenied(payment.getLifecycleStatus());
        switch (decision) {
            case NO_CHANGE -> {
                log.info("Capture-denied already reflected locally for payment {}", payment.getId());
                return false;
            }
            case CONFLICT -> {
                SettlementStateGuard.logConflict("CAPTURE_DENIED", captureId, payment.getLifecycleStatus(), PaymentLifecycleStatus.DECLINED);
                auditConflict(payment.getDealId(), "WEBHOOK_CONFLICT_CAPTURE_DENIED", captureId, payment.getLifecycleStatus(), PaymentLifecycleStatus.DECLINED);
                return false;
            }
            case APPLY -> {
                payment.setPaypalCaptureId(captureId);
                payment.setLifecycleStatus(PaymentLifecycleStatus.DECLINED);
                payment.setCaptureAttemptedAt(null);
                payment.setUpdatedAt(Instant.now());
                paymentRepository.save(payment);
                auditEventPort.recordEvent(payment.getDealId(), "WEBHOOK_RECONCILED_CAPTURE_DENIED", Map.of("captureId", captureId));
                return true;
            }
            default -> throw new IllegalStateException("Unexpected guard result " + decision);
        }
    }

    private void finalizeDealAfterCapture(UUID dealId) {
        DealStatus status;
        try {
            status = dealPort.getDealStatus(dealId);
        } catch (RuntimeException e) {
            log.warn("Could not read deal {} after capture completion: {}", dealId, e.getMessage());
            return;
        }
        if (status == DealStatus.DELIVERABLES_SUBMITTED) {
            transitionQuietly(dealId, DealStatus.ACCEPTED);
            status = DealStatus.ACCEPTED;
        }
        if (status == DealStatus.ACCEPTED) {
            transitionQuietly(dealId, DealStatus.CLOSED);
        }
    }

    private void transitionQuietly(UUID dealId, DealStatus targetStatus) {
        try {
            dealPort.transitionDealStatus(dealId, targetStatus);
        } catch (RuntimeException e) {
            log.warn("Could not transition deal {} to {} after webhook reconciliation: {}",
                    dealId, targetStatus, e.getMessage());
        }
    }



    private Optional<Agreement> findUniqueAgreementByTermsHash(String termsHashSha256) {
        java.util.List<Agreement> matches = agreementRepository.findByTermsHashSha256(termsHashSha256);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        java.util.Set<UUID> dealIds = new java.util.HashSet<>();
        for (Agreement agreement : matches) {
            dealIds.add(agreement.getDealId());
        }
        if (dealIds.size() != 1) {
            log.warn("Cannot link invoice: {} distinct deals share terms hash {}", dealIds.size(), termsHashSha256);
            return Optional.empty();
        }
        Agreement latest = matches.stream()
                .max(java.util.Comparator.comparing(Agreement::getVersion))
                .orElse(null);
        return Optional.ofNullable(latest);
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

    private boolean handleInvoicePaid(String invoiceId) {
        Optional<InvoiceEntity> invoiceOpt = invoiceRepository.findByPaypalInvoiceId(invoiceId);
        if (invoiceOpt.isEmpty()) {
            log.info("Invoice-paid ignored: no local invoice for paypalInvoiceId={}", invoiceId);
            return false;
        }
        InvoiceEntity invoice = invoiceOpt.get();
        var decision = SettlementStateGuard.canApplyInvoicePaid(invoice.getLifecycleStatus());
        switch (decision) {
            case NO_CHANGE -> {
                log.info("Invoice-paid already reflected locally for paypalInvoiceId={}", invoiceId);
                return false;
            }
            case CONFLICT -> {
                SettlementStateGuard.logConflict("INVOICE_PAID", invoiceId, invoice.getLifecycleStatus(), InvoiceLifecycleStatus.PAID);
                auditConflict(invoice.getDealId(), "WEBHOOK_CONFLICT_INVOICE_PAID", invoiceId, invoice.getLifecycleStatus(), InvoiceLifecycleStatus.PAID);
                return false;
            }
            case APPLY -> {
                invoice.setLifecycleStatus(InvoiceLifecycleStatus.PAID);
                invoice.setUpdatedAt(Instant.now());
                invoiceRepository.save(invoice);
                auditEventPort.recordEvent(invoice.getDealId(), "WEBHOOK_RECONCILED_INVOICE_PAID", Map.of("invoiceId", invoiceId));
                return true;
            }
            default -> throw new IllegalStateException("Unexpected guard result " + decision);
        }
    }

    private boolean handleInvoiceCancelled(String invoiceId) {
        Optional<InvoiceEntity> invoiceOpt = invoiceRepository.findByPaypalInvoiceId(invoiceId);
        if (invoiceOpt.isEmpty()) {
            log.info("Invoice-cancelled ignored: no local invoice for paypalInvoiceId={}", invoiceId);
            return false;
        }
        InvoiceEntity invoice = invoiceOpt.get();
        var decision = SettlementStateGuard.canApplyInvoiceCancelled(invoice.getLifecycleStatus());
        switch (decision) {
            case NO_CHANGE -> {
                log.info("Invoice-cancelled already reflected locally for paypalInvoiceId={}", invoiceId);
                return false;
            }
            case CONFLICT -> {
                SettlementStateGuard.logConflict("INVOICE_CANCELLED", invoiceId, invoice.getLifecycleStatus(), InvoiceLifecycleStatus.CANCELLED);
                auditConflict(invoice.getDealId(), "WEBHOOK_CONFLICT_INVOICE_CANCELLED", invoiceId, invoice.getLifecycleStatus(), InvoiceLifecycleStatus.CANCELLED);
                return false;
            }
            case APPLY -> {
                invoice.setLifecycleStatus(InvoiceLifecycleStatus.CANCELLED);
                invoice.setUpdatedAt(Instant.now());
                invoiceRepository.save(invoice);
                auditEventPort.recordEvent(invoice.getDealId(), "WEBHOOK_RECONCILED_INVOICE_CANCELLED", Map.of("invoiceId", invoiceId));
                return true;
            }
            default -> throw new IllegalStateException("Unexpected guard result " + decision);
        }
    }

    private boolean handleInvoiceCreated(String invoiceId, JsonNode invoiceNode) {
        if (invoiceNode == null || invoiceId == null || invoiceId.isBlank()) {
            log.warn("Invoice-created webhook ignored: missing invoice node or id");
            return false;
        }
        try {
            Optional<InvoiceEntity> already = invoiceRepository.findByPaypalInvoiceId(invoiceId);
            if (already.isPresent()) {
                InvoiceEntity link = already.get();
                log.info("Invoice-created webhook for already-linked invoice {} (deal {})", invoiceId, link.getDealId());
                return true;
            }
            String note = invoiceNode.path("detail").path("note").asText(null);
            String termsHash = extractTermsHash(note);
            if (termsHash == null) {
                log.warn("Invoice-created webhook ignored: no terms hash in detail.note (invoice {})", invoiceId);
                return false;
            }
            Optional<Agreement> agreementOpt = findUniqueAgreementByTermsHash(termsHash);
            if (agreementOpt.isEmpty()) {
                log.warn("Invoice-created webhook ignored: no unique deal matches terms hash {}", termsHash);
                return false;
            }
            Agreement agreement = agreementOpt.get();
            String canonical = agreement.getCanonicalTermsJson();
            com.payagent.common.contracts.StructuredTermsDTO canonicalTerms = readTerms(canonical);
            if (canonicalTerms == null) {
                log.warn("Invoice-created webhook ignored: could not read agreement terms for deal {}", agreement.getDealId());
                return false;
            }
            long amountMinor = parseAmountToMinorUnits(invoiceNode.path("amount").path("value").asText(null));
            String currency = invoiceNode.path("amount").path("currency_code").asText(null);
            if (amountMinor < 0 || currency == null || amountMinor != canonicalTerms.priceMinorUnits()
                    || !currency.equalsIgnoreCase(canonicalTerms.currency())) {
                log.warn("Invoice-created webhook ignored: amount/currency mismatch for terms hash {}", termsHash);
                return false;
            }
            String buyerEmail = dealPort.getBuyerEmail(agreement.getDealId());
            String sellerEmail = dealPort.getSellerEmail(agreement.getDealId());
            String recipient = invoiceNode.path("primary_recipients").path(0).path("billing_info").path("email_address").asText(null);
            String invoicer = invoiceNode.path("invoicer").path("email_address").asText(null);
            if (!equalsIgnoreCase(recipient, buyerEmail) || !equalsIgnoreCase(invoicer, sellerEmail)) {
                log.warn("Invoice-created webhook ignored: buyer/seller email mismatch (terms hash {})", termsHash);
                return false;
            }
            Optional<InvoiceEntity> dealInvoice = invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(agreement.getDealId());
            if (dealInvoice.isPresent()) {
                log.warn("Cannot link invoice {}: deal {} already has an invoice ({})", invoiceId, agreement.getDealId(),
                        dealInvoice.get().getPaypalInvoiceId());
                return false;
            }
            InvoiceLifecycleStatus status = mapInvoiceStatus(invoiceNode.path("status").asText("DRAFT"));
            String invoiceNumber = invoiceNode.path("detail").path("invoice_number").asText(null);
            String viewUrl = viewUrlFor(invoiceId, invoiceNode);
            InvoiceEntity entity = new InvoiceEntity(
                    UUID.randomUUID(),
                    agreement.getDealId(),
                    agreement.getVersion(),
                    status,
                    invoiceId,
                    invoiceNumber,
                    viewUrl,
                    amountMinor,
                    currency.toUpperCase());
            invoiceRepository.save(entity);
            auditEventPort.recordEvent(agreement.getDealId(), "WEBHOOK_RECONCILED_INVOICE_CREATED",
                    Map.of("paypalInvoiceId", invoiceId, "status", status.name(), "amountMinorUnits", amountMinor));
            log.info("Linked existing PayPal invoice {} to deal {} (agreement v{})", invoiceId, agreement.getDealId(), agreement.getVersion());
            return true;
        } catch (Exception e) {
            log.warn("Invoice-created webhook handling failed for {}: {}", invoiceId, e.getMessage());
            return false;
        }
    }

    private boolean handleInvoiceSent(String invoiceId, JsonNode invoiceNode) {
        Optional<InvoiceEntity> invoiceOpt = invoiceRepository.findByPaypalInvoiceId(invoiceId);
        if (invoiceOpt.isEmpty()) {
            log.info("Invoice-sent ignored: no local invoice for paypalInvoiceId={}", invoiceId);
            return false;
        }
        InvoiceEntity invoice = invoiceOpt.get();
        if (invoice.getLifecycleStatus() == InvoiceLifecycleStatus.SENT
                || invoice.getLifecycleStatus() == InvoiceLifecycleStatus.PAID) {
            return false;
        }
        invoice.setLifecycleStatus(InvoiceLifecycleStatus.SENT);
        invoice.setPaypalInvoiceViewUrl(viewUrlFor(invoiceId, invoiceNode));
        invoice.setUpdatedAt(Instant.now());
        invoiceRepository.save(invoice);
        auditEventPort.recordEvent(invoice.getDealId(), "WEBHOOK_RECONCILED_INVOICE_SENT",
                Map.of("paypalInvoiceId", invoiceId));
        return true;
    }

    private com.payagent.common.contracts.StructuredTermsDTO readTerms(String canonicalJson) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(canonicalJson, com.payagent.common.contracts.StructuredTermsDTO.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractTermsHash(String note) {
        if (note == null) {
            return null;
        }
        int idx = note.indexOf("termsHashSha256:");
        if (idx < 0) {
            return null;
        }
        String tail = note.substring(idx + "termsHashSha256:".length()).trim();
        int separator = tail.indexOf(' ');
        if (separator > 0) {
            tail = tail.substring(0, separator);
        }
        if (tail.length() != 64) {
            return null;
        }
        for (int i = 0; i < tail.length(); i++) {
            char c = tail.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return null;
            }
        }
        return tail;
    }

    private static long parseAmountToMinorUnits(String decimalValue) {
        try {
            java.math.BigDecimal parsed = new java.math.BigDecimal(decimalValue);
            return parsed.movePointRight(2).toBigIntegerExact().longValueExact();
        } catch (Exception e) {
            return -1L;
        }
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private static InvoiceLifecycleStatus mapInvoiceStatus(String status) {
        if (status == null) {
            return InvoiceLifecycleStatus.UNKNOWN;
        }
        return switch (status.toUpperCase()) {
            case "DRAFT" -> InvoiceLifecycleStatus.DRAFT;
            case "SENT", "SCHEDULED" -> InvoiceLifecycleStatus.SENT;
            case "PAID", "MARKED_AS_PAID" -> InvoiceLifecycleStatus.PAID;
            case "CANCELLED", "REFUNDED" -> InvoiceLifecycleStatus.CANCELLED;
            default -> InvoiceLifecycleStatus.UNKNOWN;
        };
    }

    private static String viewUrlFor(String invoiceId, JsonNode invoiceNode) {
        if (invoiceNode != null) {
            JsonNode links = invoiceNode.path("links");
            if (links.isArray()) {
                for (JsonNode link : links) {
                    if ("payer-view".equalsIgnoreCase(link.path("rel").asText())) {
                        return link.path("href").asText(null);
                    }
                }
            }
        }
        return "https://www.sandbox.paypal.com/invoice/payerView/details/" + invoiceId;
    }

    private void auditConflict(UUID dealId, String eventType, String resourceId, Enum<?> currentState, Enum<?> incomingState) {
        try {
            auditEventPort.recordEvent(dealId, eventType, Map.of(
                    "resourceId", resourceId,
                    "currentState", String.valueOf(currentState),
                    "incomingState", String.valueOf(incomingState),
                    "resolution", "LOCAL_STATE_PRESERVED_PENDING_RECONCILIATION"
            ));
        } catch (Exception e) {
            // Never let observability failure hide the transition decision; the conflict is logged.
            log.warn("Failed to persist conflict audit event {} for deal {}: {}", eventType, dealId, e.getMessage());
        }
    }

    private void markInvoicePaidIfEligible(PaymentEntity payment) {
        invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(payment.getDealId()).ifPresent(invoice -> {
            var invoiceDecision = SettlementStateGuard.canApplyInvoicePaid(invoice.getLifecycleStatus());
            if (invoiceDecision == SettlementStateGuard.Result.APPLY) {
                invoice.setLifecycleStatus(InvoiceLifecycleStatus.PAID);
                invoice.setUpdatedAt(Instant.now());
                invoiceRepository.save(invoice);
                auditEventPort.recordEvent(payment.getDealId(), "WEBHOOK_RECONCILED_INVOICE_PAID",
                        Map.of("invoiceId", String.valueOf(invoice.getPaypalInvoiceId())));
            } else if (invoiceDecision == SettlementStateGuard.Result.CONFLICT) {
                SettlementStateGuard.logConflict("INVOICE_PAID_FROM_CAPTURE", String.valueOf(invoice.getPaypalInvoiceId()),
                        invoice.getLifecycleStatus(), InvoiceLifecycleStatus.PAID);
                auditConflict(invoice.getDealId(), "WEBHOOK_CONFLICT_INVOICE_PAID_FROM_CAPTURE",
                        String.valueOf(invoice.getPaypalInvoiceId()), invoice.getLifecycleStatus(), InvoiceLifecycleStatus.PAID);
            }
        });
    }

    /**
     * Recovery for payments whose capture was attempted but whose outcome was
     * not persisted locally (e.g. application crash after PayPal processed the
     * capture). The authoritative PayPal order/authorization state is queried;
     * this method never captures funds.
     */
    @Scheduled(
            initialDelayString = "${payagent.paypal.capture-recovery.initial-delay-ms:90000}",
            fixedDelayString = "${payagent.paypal.capture-recovery.interval-ms:300000}"
    )
    @Transactional
    public void recoverAuthorizedCaptures() {
        List<PaymentEntity> payments = paymentRepository.findOpenCaptureAttemptsForUpdate(
                Set.of(CaptureAttemptService.OPEN, CaptureAttemptService.PENDING_RECONCILIATION),
                Set.of(PaymentLifecycleStatus.AUTHORIZED, PaymentLifecycleStatus.CAPTURE_PENDING, PaymentLifecycleStatus.UNKNOWN));
        for (PaymentEntity payment : payments) {
            try {
                recoverCaptureForPayment(payment);
            } catch (Exception e) {
                // Keep the marker for the next pass; never guess or capture again.
                log.warn("Capture recovery lookup failed for payment {}: {}", payment.getId(), e.getMessage());
            }
        }
    }

    private void recoverCaptureForPayment(PaymentEntity payment) {
        String orderId = payment.getPaypalOrderId();
        if (orderId == null || orderId.isBlank()) {
            // No PayPal order id: cannot establish remote outcome, so the attempt
            // remains unresolved and blocks further captures. Do not clear.
            payment.setCaptureResolution(CaptureAttemptService.PENDING_RECONCILIATION);
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);
            log.warn("Capture recovery could not resolve payment {} because no PayPal order id is available; manual reconciliation required", payment.getId());
            return;
        }

        var order = orderService.getOrder(orderId);
        if (order != null && order.purchaseUnits() != null) {
            for (var unit : order.purchaseUnits()) {
                if (unit.payments() == null || unit.payments().captures() == null) {
                    continue;
                }
                for (var capture : unit.payments().captures()) {
                    if (capture == null || capture.id() == null) {
                        continue;
                    }
                    PaymentLifecycleStatus mapped = orderService.mapCaptureStatus(capture.status());
                    if (mapped == null) {
                        mapped = PaymentLifecycleStatus.UNKNOWN;
                    }
                    var decision = switch (mapped) {
                        case COMPLETED -> SettlementStateGuard.canApplyCaptureCompleted(payment.getLifecycleStatus());
                        case CAPTURE_PENDING -> SettlementStateGuard.canApplyCapturePending(payment.getLifecycleStatus());
                        default -> null;
                    };
                    if (decision == null) {
                        log.warn("Capture recovery found capture {} with unsupported status {} for payment {}", capture.id(), capture.status(), payment.getId());
                        return;
                    }
                    switch (decision) {
                        case NO_CHANGE -> log.info("Capture recovery: payment {} already reflects capture {}", payment.getId(), capture.id());
                        case CONFLICT -> {
                            SettlementStateGuard.logConflict("CAPTURE_RECOVERY", capture.id(), payment.getLifecycleStatus(), mapped);
                            auditConflict(payment.getDealId(), "RECOVERY_CONFLICT_CAPTURE", capture.id(), payment.getLifecycleStatus(), mapped);
                        }
                        case APPLY -> {
                            payment.setPaypalCaptureId(capture.id());
                            payment.setLifecycleStatus(mapped);
                            payment.setCaptureAttemptedAt(null);
                            payment.setCaptureResolution(CaptureAttemptService.RESOLVED);
                            payment.setUpdatedAt(Instant.now());
                            paymentRepository.save(payment);
                            if (mapped == PaymentLifecycleStatus.COMPLETED) {
                                markInvoicePaidIfEligible(payment);
                            }
                            auditEventPort.recordEvent(payment.getDealId(), "RECOVERY_CAPTURE_" + mapped.name(),
                                    Map.of("captureId", capture.id(), "orderId", orderId));
                        }
                    }
                    return;
                }
            }
        }

        // No capture exists on the order. Check the authorization: PayPal reports
        // authorization status AUTHORIZED (or voided/denied) when no capture happened.
        String authorizationId = payment.getPaypalAuthorizationId();
        if (authorizationId != null && !authorizationId.isBlank()) {
            var authorization = orderService.getAuthorization(authorizationId);
            PaymentLifecycleStatus mapped = orderService.mapAuthorizationStatus(authorization.status());
            switch (mapped) {
                case VOIDED -> {
                    if (SettlementStateGuard.canApplyAuthorizationVoided(payment.getLifecycleStatus()) == SettlementStateGuard.Result.APPLY) {
                        payment.setLifecycleStatus(PaymentLifecycleStatus.VOIDED);
                        payment.setCaptureAttemptedAt(null);
                        payment.setCaptureResolution(CaptureAttemptService.RESOLVED);
                        payment.setUpdatedAt(Instant.now());
                        paymentRepository.save(payment);
                        auditEventPort.recordEvent(payment.getDealId(), "RECOVERY_AUTHORIZATION_VOIDED",
                                Map.of("authorizationId", authorizationId));
                    } else {
                        payment.setCaptureResolution(CaptureAttemptService.PENDING_RECONCILIATION);
                        payment.setUpdatedAt(Instant.now());
                        paymentRepository.save(payment);
                    }
                    return;
                }
                case DECLINED -> {
                    if (SettlementStateGuard.canApplyCaptureDenied(payment.getLifecycleStatus()) == SettlementStateGuard.Result.APPLY) {
                        payment.setLifecycleStatus(PaymentLifecycleStatus.DECLINED);
                        payment.setCaptureAttemptedAt(null);
                        payment.setCaptureResolution(CaptureAttemptService.RESOLVED);
                        payment.setUpdatedAt(Instant.now());
                        paymentRepository.save(payment);
                        auditEventPort.recordEvent(payment.getDealId(), "RECOVERY_AUTHORIZATION_DENIED",
                                Map.of("authorizationId", authorizationId));
                    } else {
                        payment.setCaptureResolution(CaptureAttemptService.PENDING_RECONCILIATION);
                        payment.setUpdatedAt(Instant.now());
                        paymentRepository.save(payment);
                    }
                    return;
                }
                case COMPLETED -> {
                    // PayPal reports the authorization as captured but the order payload did not
                    // include the capture detail; do not assume failure. Mark for review instead of guessing.
                    payment.setLifecycleStatus(PaymentLifecycleStatus.UNKNOWN);
                    payment.setCaptureResolution(CaptureAttemptService.PENDING_RECONCILIATION);
                    payment.setUpdatedAt(Instant.now());
                    paymentRepository.save(payment);
                    auditEventPort.recordEvent(payment.getDealId(), "RECOVERY_AUTHORIZATION_CAPTURED_NEEDS_CAPTURE_ID",
                            Map.of("authorizationId", authorizationId, "orderId", orderId));
                    return;
                }
                case AUTHORIZED, UNKNOWN -> {
                    // Remote outcome is not conclusively resolved; keep the attempt open and block any blind retry.
                    payment.setCaptureResolution(CaptureAttemptService.PENDING_RECONCILIATION);
                    payment.setUpdatedAt(Instant.now());
                    paymentRepository.save(payment);
                    log.warn("Capture recovery could not resolve payment {}; PayPal authorization status={} — still awaiting reconciliation",
                            payment.getId(), authorization.status());
                    return;
                }
                default -> {
                    log.warn("Capture recovery found unresolved authorization status {} for payment {}", authorization.status(), payment.getId());
                    return;
                }
            }
        }

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
