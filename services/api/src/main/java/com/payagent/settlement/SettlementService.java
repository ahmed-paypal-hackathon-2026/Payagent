package com.payagent.settlement;

import com.payagent.common.contracts.AgreementSnapshotDTO;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.InvoiceLifecycleStatus;
import com.payagent.common.contracts.PaymentLifecycleStatus;
import com.payagent.common.contracts.SettlementStateDTO;
import com.payagent.paypal.PayPalInvoiceService;
import com.payagent.paypal.PayPalOrderService;
import com.payagent.paypal.model.AuthorizeOrderResult;
import com.payagent.paypal.model.CreateInvoiceCommand;
import com.payagent.paypal.model.CreateOrderCommand;
import com.payagent.paypal.model.PayPalCaptureResponse;
import com.payagent.paypal.model.PayPalInvoiceResponse;
import com.payagent.paypal.model.PayPalOrderResponse;
import com.payagent.settlement.exception.SettlementConflictException;
import com.payagent.settlement.exception.SettlementNotFoundException;
import com.payagent.settlement.persistence.InvoiceEntity;
import com.payagent.settlement.persistence.InvoiceRepository;
import com.payagent.settlement.persistence.PaymentEntity;
import com.payagent.settlement.persistence.PaymentRepository;
import com.payagent.settlement.port.AuditEventPort;
import com.payagent.settlement.port.DealPort;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final PayPalInvoiceService invoiceService;
    private final PayPalOrderService orderService;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final DealPort dealPort;
    private final AuditEventPort auditEventPort;

    // Concurrency guard to prevent double capture attempts on the same deal
    private final Map<UUID, Object> captureLocks = new ConcurrentHashMap<>();

    public SettlementService(PayPalInvoiceService invoiceService,
                             PayPalOrderService orderService,
                             InvoiceRepository invoiceRepository,
                             PaymentRepository paymentRepository,
                             DealPort dealPort,
                             AuditEventPort auditEventPort) {
        this.invoiceService = invoiceService;
        this.orderService = orderService;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.dealPort = dealPort;
        this.auditEventPort = auditEventPort;
    }

    @Transactional
    public SettlementStateDTO initiateSettlement(UUID dealId, String idempotencyKey, String callerEmail) {
        dealPort.assertParty(dealId, callerEmail);

        DealStatus currentStatus = dealPort.getDealStatus(dealId);
        if (currentStatus != DealStatus.AGREED && currentStatus != DealStatus.SETTLEMENT_PENDING) {
            throw new SettlementConflictException("Cannot initiate settlement: deal " + dealId + " is in " + currentStatus + " status, required AGREED");
        }

        Optional<InvoiceEntity> existingInvoice = invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId);
        if (existingInvoice.isPresent()
                && existingInvoice.get().getPaypalInvoiceId() != null
                && existingInvoice.get().getLifecycleStatus() != InvoiceLifecycleStatus.CANCELLED) {
            Optional<PaymentEntity> existingPayment = paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId);
            log.info("Settlement already initiated for deal {}. Returning existing state instead of creating duplicate invoices.", dealId);
            return assembleStateDTO(
                    dealId,
                    existingInvoice.get().getAgreementVersion(),
                    existingInvoice.get(),
                    existingPayment.orElse(null)
            );
        }

        AgreementSnapshotDTO agreement = dealPort.getAgreedAgreement(dealId);
        var canonicalTerms = agreement.canonicalTerms();

        String sellerEmail = dealPort.getSellerEmail(dealId);
        String buyerEmail = dealPort.getBuyerEmail(dealId);

        String invRequestId = idempotencyKey != null ? idempotencyKey + "-inv" : UUID.randomUUID().toString();
        CreateInvoiceCommand invoiceCommand = new CreateInvoiceCommand(
                dealId.toString(),
                sellerEmail,
                buyerEmail,
                canonicalTerms.title(),
                canonicalTerms.deliverables(),
                canonicalTerms.priceMinorUnits(),
                canonicalTerms.currency(),
                agreement.termsHashSha256()
        );

        // 1. Create and send PayPal Invoicing v2 invoice
        PayPalInvoiceResponse invoiceResponse = invoiceService.createDraftInvoice(invoiceCommand, invRequestId);
        invoiceService.sendInvoice(invoiceResponse.id(), invRequestId + "-send");

        InvoiceEntity invoiceEntity = invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)
                .orElseGet(() -> new InvoiceEntity(
                        UUID.randomUUID(),
                        dealId,
                        agreement.version(),
                        InvoiceLifecycleStatus.SENT,
                        invoiceResponse.id(),
                        invoiceResponse.detail() != null ? invoiceResponse.detail().invoiceNumber() : null,
                        invoiceResponse.getPayerViewUrl(),
                        canonicalTerms.priceMinorUnits(),
                        canonicalTerms.currency()
                ));

        invoiceEntity.setLifecycleStatus(InvoiceLifecycleStatus.SENT);
        invoiceEntity.setPaypalInvoiceId(invoiceResponse.id());
        if (invoiceResponse.detail() != null) {
            invoiceEntity.setPaypalInvoiceNumber(invoiceResponse.detail().invoiceNumber());
        }
        invoiceEntity.setPaypalInvoiceViewUrl(invoiceResponse.getPayerViewUrl());
        invoiceEntity.setUpdatedAt(Instant.now());
        invoiceRepository.save(invoiceEntity);

        auditEventPort.recordEvent(dealId, "INVOICE_SENT", Map.of(
                "paypalInvoiceId", invoiceResponse.id(),
                "amountMinorUnits", canonicalTerms.priceMinorUnits(),
                "currency", canonicalTerms.currency()
        ));

        // 2. Create PayPal Orders v2 Order with intent: AUTHORIZE, linked to invoice
        String ordRequestId = idempotencyKey != null ? idempotencyKey + "-ord" : UUID.randomUUID().toString();
        CreateOrderCommand orderCommand = new CreateOrderCommand(
                dealId.toString(),
                invoiceResponse.id(),
                canonicalTerms.title(),
                canonicalTerms.priceMinorUnits(),
                canonicalTerms.currency()
        );

        PayPalOrderResponse orderResponse = orderService.createOrder(orderCommand, ordRequestId);

        PaymentEntity paymentEntity = paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)
                .orElseGet(() -> new PaymentEntity(
                        UUID.randomUUID(),
                        dealId,
                        agreement.version(),
                        PaymentLifecycleStatus.ORDER_CREATED,
                        orderResponse.id(),
                        null,
                        null,
                        null,
                        canonicalTerms.priceMinorUnits(),
                        canonicalTerms.currency()
                ));

        paymentEntity.setLifecycleStatus(PaymentLifecycleStatus.ORDER_CREATED);
        paymentEntity.setPaypalOrderId(orderResponse.id());
        paymentEntity.setUpdatedAt(Instant.now());
        paymentRepository.save(paymentEntity);

        auditEventPort.recordEvent(dealId, "PAYMENT_ORDER_CREATED", Map.of(
                "paypalOrderId", orderResponse.id(),
                "linkedInvoiceId", invoiceResponse.id()
        ));

        dealPort.transitionDealStatus(dealId, DealStatus.SETTLEMENT_PENDING);

        return assembleStateDTO(dealId, agreement.version(), invoiceEntity, paymentEntity);
    }

    @Transactional
    public SettlementStateDTO authorizeSettlement(UUID dealId, String paypalOrderId, String idempotencyKey, String callerEmail) {
        dealPort.assertParty(dealId, callerEmail);

        PaymentEntity payment = paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)
                .orElseThrow(() -> new SettlementNotFoundException("Payment record not found for deal " + dealId));

        // Replays must not create a second authorization hold.
        if (payment.getLifecycleStatus() == PaymentLifecycleStatus.AUTHORIZED
                || payment.getLifecycleStatus() == PaymentLifecycleStatus.COMPLETED) {
            InvoiceEntity invoice = invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId).orElse(null);
            return assembleStateDTO(dealId, payment.getAgreementVersion(), invoice, payment);
        }

        if (payment.getLifecycleStatus() != PaymentLifecycleStatus.ORDER_CREATED
                && payment.getLifecycleStatus() != PaymentLifecycleStatus.UNKNOWN) {
            throw new SettlementConflictException("Cannot authorize: payment is in status "
                    + payment.getLifecycleStatus() + " (expected ORDER_CREATED)");
        }

        String orderId = paypalOrderId != null && !paypalOrderId.isBlank() ? paypalOrderId : payment.getPaypalOrderId();
        if (orderId == null || orderId.isBlank()) {
            throw new SettlementConflictException("No PayPal order ID available for authorization on deal " + dealId);
        }

        String authRequestId = idempotencyKey != null ? idempotencyKey + "-auth" : UUID.randomUUID().toString();
        AuthorizeOrderResult authResult = orderService.authorizeOrder(orderId, authRequestId);

        payment.setPaypalOrderId(orderId);
        payment.setPaypalAuthorizationId(authResult.authorizationId());
        payment.setLifecycleStatus(PaymentLifecycleStatus.AUTHORIZED);
        if (authResult.expirationTime() != null) {
            try {
                payment.setAuthorizationExpiresAt(Instant.parse(authResult.expirationTime()));
            } catch (Exception e) {
                log.warn("Could not parse authorization expiry: {}", authResult.expirationTime());
            }
        }
        payment.setUpdatedAt(Instant.now());
        paymentRepository.save(payment);

        auditEventPort.recordEvent(dealId, "PAYMENT_AUTHORIZED", Map.of(
                "paypalAuthorizationId", authResult.authorizationId(),
                "status", authResult.status()
        ));

        dealPort.transitionDealStatus(dealId, DealStatus.IN_PROGRESS);

        InvoiceEntity invoice = invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId).orElse(null);
        return assembleStateDTO(dealId, payment.getAgreementVersion(), invoice, payment);
    }

    @Transactional
    public SettlementStateDTO acceptDeliverables(UUID dealId, int evidenceVersion, String idempotencyKey, String callerEmail) {
        // Invariant 1: BUYER only
        dealPort.assertBuyer(dealId, callerEmail);

        // Invariant 2: Dispute freeze — strictly block capture if DISPUTED
        if (dealPort.isDealDisputed(dealId)) {
            log.warn("Capture blocked: deal {} is in DISPUTED status", dealId);
            throw new SettlementConflictException("Capture blocked: deal is in DISPUTED status");
        }

        Object lock = captureLocks.computeIfAbsent(dealId, k -> new Object());
        synchronized (lock) {
            // Re-check after acquiring the lock. The DB row lock also serializes this
            // against Member E's dispute transition, closing the capture/dispute race.
            DealStatus lockedStatus = dealPort.lockDealAndGetStatus(dealId);
            if (lockedStatus == DealStatus.DISPUTED
                    || dealPort.hasOpenDispute(dealId)
                    || dealPort.isDealDisputed(dealId)) {
                log.warn("Capture blocked after lock: deal {} is in DISPUTED status", dealId);
                throw new SettlementConflictException("Capture blocked: deal is in DISPUTED status");
            }

            PaymentEntity payment = paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)
                    .orElseThrow(() -> new SettlementNotFoundException("Payment record not found for deal " + dealId));

            if (payment.getLifecycleStatus() == PaymentLifecycleStatus.COMPLETED) {
                log.info("Payment for deal {} is already completed. Preventing double capture.", dealId);
                InvoiceEntity invoice = invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId).orElse(null);
                return assembleStateDTO(dealId, payment.getAgreementVersion(), invoice, payment);
            }

            if (lockedStatus != DealStatus.DELIVERABLES_SUBMITTED) {
                throw new SettlementConflictException("Cannot accept deliverables: deal is in "
                        + lockedStatus + " status, required DELIVERABLES_SUBMITTED");
            }

            if (payment.getLifecycleStatus() != PaymentLifecycleStatus.AUTHORIZED || payment.getPaypalAuthorizationId() == null) {
                throw new SettlementConflictException("Cannot capture: payment is in status " + payment.getLifecycleStatus() + " (expected AUTHORIZED)");
            }

            // Verify authorization expiry
            if (payment.getAuthorizationExpiresAt() != null && Instant.now().isAfter(payment.getAuthorizationExpiresAt())) {
                throw new SettlementConflictException("Cannot capture: PayPal authorization has expired");
            }

            String capRequestId = idempotencyKey != null ? idempotencyKey + "-cap" : UUID.randomUUID().toString();
            PayPalCaptureResponse capture = orderService.captureAuthorization(
                    payment.getPaypalAuthorizationId(),
                    payment.getAmountMinor(),
                    payment.getCurrency(),
                    capRequestId
            );

            PaymentLifecycleStatus captureStatus = orderService.mapCaptureStatus(capture.status());
            if (captureStatus == null) {
                captureStatus = PaymentLifecycleStatus.UNKNOWN;
            }

            payment.setPaypalCaptureId(capture.id());
            payment.setLifecycleStatus(captureStatus);
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);

            auditEventPort.recordEvent(dealId, "PAYMENT_CAPTURE_" + captureStatus.name(), Map.of(
                    "paypalCaptureId", capture.id(),
                    "amountMinorUnits", payment.getAmountMinor(),
                    "evidenceVersion", evidenceVersion
            ));

            InvoiceEntity invoice = invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId).orElse(null);

            if (captureStatus == PaymentLifecycleStatus.COMPLETED) {
                // Record payment on invoice
                if (invoice != null && invoice.getPaypalInvoiceId() != null) {
                    try {
                        String invPayRequestId = idempotencyKey != null ? idempotencyKey + "-invpay" : UUID.randomUUID().toString();
                        invoiceService.recordPayment(
                                invoice.getPaypalInvoiceId(),
                                payment.getAmountMinor(),
                                payment.getCurrency(),
                                "Settlement capture completed for evidence v" + evidenceVersion,
                                invPayRequestId
                        );
                        invoice.setLifecycleStatus(InvoiceLifecycleStatus.PAID);
                        invoice.setUpdatedAt(Instant.now());
                        invoiceRepository.save(invoice);

                        auditEventPort.recordEvent(dealId, "INVOICE_PAID", Map.of(
                                "paypalInvoiceId", invoice.getPaypalInvoiceId()
                        ));
                    } catch (Exception e) {
                        log.error("Failed to record payment on invoice {}: {}", invoice.getPaypalInvoiceId(), e.getMessage());
                    }
                }

                dealPort.transitionDealStatus(dealId, DealStatus.ACCEPTED);
                dealPort.transitionDealStatus(dealId, DealStatus.CLOSED);
            } else {
                log.warn("Capture for deal {} returned status {}. Settlement remains in progress pending webhook reconciliation.",
                        dealId, captureStatus);
            }

            return assembleStateDTO(dealId, payment.getAgreementVersion(), invoice, payment);
        }
    }

    @Transactional
    public SettlementStateDTO voidSettlement(UUID dealId, String reason, String idempotencyKey, String callerEmail) {
        dealPort.assertParty(dealId, callerEmail);

        PaymentEntity payment = paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId).orElse(null);
        InvoiceEntity invoice = invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId).orElse(null);

        // A completed capture can only be reversed through a refund/dispute resolution,
        // never by voiding an authorization that no longer exists.
        if (payment != null && payment.getLifecycleStatus() == PaymentLifecycleStatus.COMPLETED) {
            throw new SettlementConflictException("Cannot void settlement: payment is already captured");
        }

        if (payment != null && payment.getLifecycleStatus() == PaymentLifecycleStatus.AUTHORIZED
                && payment.getPaypalAuthorizationId() != null) {
            String voidRequestId = idempotencyKey != null ? idempotencyKey + "-void" : UUID.randomUUID().toString();
            orderService.voidAuthorization(payment.getPaypalAuthorizationId(), voidRequestId);
            payment.setLifecycleStatus(PaymentLifecycleStatus.VOIDED);
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);

            auditEventPort.recordEvent(dealId, "AUTHORIZATION_VOIDED", Map.of(
                    "paypalAuthorizationId", payment.getPaypalAuthorizationId(),
                    "reason", reason != null ? reason : "Mutual cancellation or dispute void"
            ));
        }

        if (invoice != null
                && invoice.getPaypalInvoiceId() != null
                && (invoice.getLifecycleStatus() == InvoiceLifecycleStatus.DRAFT
                || invoice.getLifecycleStatus() == InvoiceLifecycleStatus.SENT)) {
            String cancelRequestId = idempotencyKey != null ? idempotencyKey + "-cancel" : UUID.randomUUID().toString();
            invoiceService.cancelInvoice(invoice.getPaypalInvoiceId(), reason, cancelRequestId);
            invoice.setLifecycleStatus(InvoiceLifecycleStatus.CANCELLED);
            invoice.setUpdatedAt(Instant.now());
            invoiceRepository.save(invoice);

            auditEventPort.recordEvent(dealId, "INVOICE_CANCELLED", Map.of(
                    "paypalInvoiceId", invoice.getPaypalInvoiceId(),
                    "reason", reason != null ? reason : "Cancelled"
            ));
        }

        DealStatus dealStatus = dealPort.getDealStatus(dealId);
        if (dealStatus != DealStatus.DISPUTED
                && dealStatus != DealStatus.ACCEPTED
                && dealStatus != DealStatus.CLOSED
                && dealStatus != DealStatus.CANCELLED) {
            dealPort.transitionDealStatus(dealId, DealStatus.CANCELLED);
        }

        int version = payment != null ? payment.getAgreementVersion() : (invoice != null ? invoice.getAgreementVersion() : 1);
        return assembleStateDTO(dealId, version, invoice, payment);
    }

    @Transactional(readOnly = true)
    public SettlementStateDTO getSettlementState(UUID dealId, String callerEmail) {
        dealPort.assertParty(dealId, callerEmail);

        InvoiceEntity invoice = invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId).orElse(null);
        PaymentEntity payment = paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId).orElse(null);

        if (invoice == null && payment == null) {
            throw new SettlementNotFoundException("No settlement state exists for deal " + dealId);
        }

        int version = payment != null ? payment.getAgreementVersion() : invoice.getAgreementVersion();
        return assembleStateDTO(dealId, version, invoice, payment);
    }

    private SettlementStateDTO assembleStateDTO(UUID dealId, int version, InvoiceEntity invoice, PaymentEntity payment) {
        InvoiceLifecycleStatus invoiceStatus = invoice != null ? invoice.getLifecycleStatus() : InvoiceLifecycleStatus.NOT_CREATED;
        String paypalInvoiceId = invoice != null ? invoice.getPaypalInvoiceId() : null;
        String paypalInvoiceNumber = invoice != null ? invoice.getPaypalInvoiceNumber() : null;
        String paypalInvoiceViewUrl = invoice != null ? invoice.getPaypalInvoiceViewUrl() : null;

        PaymentLifecycleStatus paymentStatus = payment != null ? payment.getLifecycleStatus() : PaymentLifecycleStatus.NOT_STARTED;
        String paypalOrderId = payment != null ? payment.getPaypalOrderId() : null;
        String paypalAuthorizationId = payment != null ? payment.getPaypalAuthorizationId() : null;
        String paypalCaptureId = payment != null ? payment.getPaypalCaptureId() : null;
        String authExpiresAt = payment != null && payment.getAuthorizationExpiresAt() != null
                ? payment.getAuthorizationExpiresAt().toString() : null;

        long amount = payment != null ? payment.getAmountMinor() : (invoice != null ? invoice.getAmountMinor() : 0L);
        String currency = payment != null ? payment.getCurrency() : (invoice != null ? invoice.getCurrency() : "USD");
        String lastReconciledAt = Instant.now().toString();

        return new SettlementStateDTO(
                dealId.toString(),
                version,
                invoiceStatus,
                paypalInvoiceId,
                paypalInvoiceNumber,
                paypalInvoiceViewUrl,
                paymentStatus,
                paypalOrderId,
                paypalAuthorizationId,
                paypalCaptureId,
                authExpiresAt,
                amount,
                currency,
                lastReconciledAt
        );
    }
}
