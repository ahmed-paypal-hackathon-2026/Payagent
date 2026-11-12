package com.payagent.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.InvoiceLifecycleStatus;
import com.payagent.common.contracts.PaymentLifecycleStatus;
import com.payagent.paypal.PayPalInvoiceService;
import com.payagent.paypal.PayPalOrderService;
import com.payagent.settlement.SettlementReconciliationService.WebhookProcessingResult;
import com.payagent.settlement.persistence.InvoiceEntity;
import com.payagent.settlement.persistence.InvoiceRepository;
import com.payagent.settlement.persistence.PayPalEventEntity;
import com.payagent.settlement.persistence.PayPalEventRepository;
import com.payagent.settlement.persistence.PaymentEntity;
import com.payagent.settlement.persistence.PaymentRepository;
import com.payagent.agreements.AgreementRepository;
import com.payagent.agreements.Agreement;
import com.payagent.settlement.port.AuditEventPort;
import com.payagent.settlement.port.DealPort;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettlementReconciliationServiceTest {

    private PayPalEventRepository eventRepository;
    private InvoiceRepository invoiceRepository;
    private PaymentRepository paymentRepository;
    private PayPalInvoiceService invoiceService;
    private PayPalOrderService orderService;
    private AuditEventPort auditEventPort;
    private DealPort dealPort;
    private AgreementRepository agreementRepository;
    private SettlementReconciliationService reconciliationService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        eventRepository = mock(PayPalEventRepository.class);
        invoiceRepository = mock(InvoiceRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        invoiceService = mock(PayPalInvoiceService.class);
        orderService = mock(PayPalOrderService.class);
        auditEventPort = mock(AuditEventPort.class);
        dealPort = mock(DealPort.class);
        agreementRepository = mock(AgreementRepository.class);

        when(eventRepository.insertIfAbsent(anyString(), anyString(), nullable(String.class), anyString())).thenReturn(1);

        reconciliationService = new SettlementReconciliationService(
                eventRepository,
                invoiceRepository,
                paymentRepository,
                invoiceService,
                orderService,
                auditEventPort,
                dealPort,
                agreementRepository
        );
        objectMapper = new ObjectMapper();
    }

    private Agreement sampleAgreementWithHash(UUID dealId, int version, String hash) {
        return new Agreement(UUID.randomUUID(), dealId, version,
                "{\"title\":\"Synthetic\",\"deliverables\":[\"a\"],\"priceMinorUnits\":98000,\"currency\":\"USD\",\"deliveryDays\":7,\"includedRevisions\":2,\"acceptanceCriteriaText\":\"x\"}",
                hash);
    }

    private String termsHash() {
        return "0f64e83c1ef12560e7c916abd1661fe0de5bf5d07e0dd2a4b022dcdc82654a7e";
    }

    private String invoicePayload(String hash) {
        return """
                {"id":"WH-SYNTH-CREATED-1","event_type":"INVOICING.INVOICE.CREATED",
                 "resource":{"invoice":{
                   "id":"INV2-SYNTH-XWZE-DK3Y-0001","status":"DRAFT",
                   "detail":{"invoice_number":"0006","note":"termsHashSha256: %s | Deliverables: Synthetic deliverable"},
                   "primary_recipients":[{"billing_info":{"email_address":"buyer@example.test"}}],
                   "invoicer":{"email_address":"seller@example.test"},
                   "amount":{"currency_code":"USD","value":"980.00"}
                 }}}
                """.formatted(hash);
    }

    @Test
    void invoiceCreatedWithMatchingHashPersistsAndIsIdempotent() throws Exception {
        UUID dealId = UUID.randomUUID();
        Agreement agreement = sampleAgreementWithHash(dealId, 1, termsHash());
        when(agreementRepository.findByTermsHashSha256(termsHash()))
                .thenReturn(java.util.List.of(agreement));
        when(dealPort.getBuyerEmail(dealId)).thenReturn("buyer@example.test");
        when(dealPort.getSellerEmail(dealId)).thenReturn("seller@example.test");
        when(invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.empty());
        when(invoiceRepository.findByPaypalInvoiceId("INV2-SYNTH-XWZE-DK3Y-0001")).thenReturn(Optional.empty());

        var result = reconciliationService.processWebhookEvent(
                "EVT-NEW-CREATED-1", "INVOICING.INVOICE.CREATED", "INV2-SYNTH-XWZE-DK3Y-0001",
                objectMapper.readTree(invoicePayload(termsHash())).path("resource").path("invoice"),
                invoicePayload(termsHash()));

        assertThat(result).isEqualTo(WebhookProcessingResult.PROCESSED);
        verify(invoiceRepository, times(1)).save(any(InvoiceEntity.class));

        // Re-delivery must not duplicate rows
        when(invoiceRepository.findByPaypalInvoiceId("INV2-SYNTH-XWZE-DK3Y-0001"))
                .thenReturn(Optional.of(new InvoiceEntity(UUID.randomUUID(), dealId, 1,
                        InvoiceLifecycleStatus.DRAFT, "INV2-SYNTH-XWZE-DK3Y-0001", "0006",
                        "url", 98000L, "USD")));
        var again = reconciliationService.processWebhookEvent(
                "EVT-NEW-CREATED-2", "INVOICING.INVOICE.CREATED", "INV2-SYNTH-XWZE-DK3Y-0001",
                objectMapper.readTree(invoicePayload(termsHash())).path("resource").path("invoice"),
                invoicePayload(termsHash()));
        assertThat(again).isEqualTo(WebhookProcessingResult.PROCESSED);
        verify(invoiceRepository, times(1)).save(any(InvoiceEntity.class));
    }

    @Test
    void invoiceCreatedWithSandboxEmailMismatchStillLinks() throws Exception {
        // Sandbox PayPal identities are never mapped to application users; the email equality
        // check must NOT gate linking when hash + amount + currency + unique-deal all pass.
        UUID dealId = UUID.randomUUID();
        Agreement agreement = sampleAgreementWithHash(dealId, 1, termsHash());
        when(agreementRepository.findByTermsHashSha256(termsHash()))
                .thenReturn(java.util.List.of(agreement));
        when(dealPort.getBuyerEmail(dealId)).thenReturn("registered-buyer@example.com");
        when(dealPort.getSellerEmail(dealId)).thenReturn("registered-seller@example.com");
        when(invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.empty());
        when(invoiceRepository.findByPaypalInvoiceId("INV2-SYNTH-XWZE-DK3Y-0001")).thenReturn(Optional.empty());

        var result = reconciliationService.processWebhookEvent(
                "EVT-EMAIL-MISMATCH", "INVOICING.INVOICE.CREATED", "INV2-SYNTH-XWZE-DK3Y-0001",
                objectMapper.readTree(invoicePayload(termsHash())).path("resource").path("invoice"),
                invoicePayload(termsHash()));

        assertThat(result).isEqualTo(WebhookProcessingResult.PROCESSED);
        verify(invoiceRepository).save(any(InvoiceEntity.class));
    }

    @Test
    void invoiceCreatedRefusesToAutoLinkWhenMultipleCandidatesExist() throws Exception {
        UUID dealId = UUID.randomUUID();
        Agreement agreement = sampleAgreementWithHash(dealId, 1, termsHash());
        when(agreementRepository.findByTermsHashSha256(termsHash()))
                .thenReturn(java.util.List.of(agreement));
        when(dealPort.getBuyerEmail(dealId)).thenReturn("registered-buyer@example.com");
        when(dealPort.getSellerEmail(dealId)).thenReturn("registered-seller@example.com");
        when(invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.empty());
        when(invoiceRepository.findByPaypalInvoiceId("INV2-SYNTH-XWZE-DK3Y-0001")).thenReturn(Optional.empty());

        String otherPayload = """
                {"id":"WH-SYNTH-CREATED-2","event_type":"INVOICING.INVOICE.CREATED",
                 "resource":{"invoice":{
                   "id":"INV2-OTHER-AAAA-BBBB-CCCC","status":"DRAFT",
                   "detail":{"invoice_number":"0007","note":"termsHashSha256: %s | Deliverables: Synthetic deliverable"},
                   "amount":{"currency_code":"USD","value":"980.00"}
                 }}}
                """.formatted(termsHash());
        when(eventRepository.findByEventTypeOrderByReceivedAtAsc("INVOICING.INVOICE.CREATED"))
                .thenReturn(java.util.List.of(
                        new com.payagent.settlement.persistence.PayPalEventEntity(
                                "EVT-A", "INVOICING.INVOICE.CREATED", "INV2-SYNTH-XWZE-DK3Y-0001",
                                invoicePayload(termsHash())),
                        new com.payagent.settlement.persistence.PayPalEventEntity(
                                "EVT-B", "INVOICING.INVOICE.CREATED", "INV2-OTHER-AAAA-BBBB-CCCC",
                                otherPayload)));

        var result = reconciliationService.processWebhookEvent(
                "EVT-AMBIG-1", "INVOICING.INVOICE.CREATED", "INV2-SYNTH-XWZE-DK3Y-0001",
                objectMapper.readTree(invoicePayload(termsHash())).path("resource").path("invoice"),
                invoicePayload(termsHash()));

        assertThat(result).isEqualTo(WebhookProcessingResult.ACKNOWLEDGED_NO_BUSINESS_MUTATION);
        verify(invoiceRepository, never()).save(any());
        verify(auditEventPort).recordEvent(eq(dealId), eq("INVOICE_LINK_AMBIGUOUS"), any());
    }

    @Test
    void invoiceCreatedWithWrongHashIsIgnored() throws Exception {
        when(agreementRepository.findByTermsHashSha256(anyString()))
                .thenReturn(java.util.List.of());
        var result = reconciliationService.processWebhookEvent(
                "EVT-CREATED-BAD", "INVOICING.INVOICE.CREATED", "INV2-SYNTH-XWZE-DK3Y-0001",
                objectMapper.readTree(invoicePayload("deadbeef".repeat(8))).path("resource").path("invoice"),
                invoicePayload("deadbeef".repeat(8)));
        assertThat(result).isEqualTo(WebhookProcessingResult.ACKNOWLEDGED_NO_BUSINESS_MUTATION);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void invoiceCreatedWithMissingInvoiceNodeIsIgnored() throws Exception {
        var result = reconciliationService.processWebhookEvent(
                "EVT-CREATED-MISSING", "INVOICING.INVOICE.CREATED", null,
                objectMapper.readTree("{\"id\":\"WH-NO-INVOICE\",\"resource\":{}}").path("resource"),
                "{}");
        assertThat(result).isEqualTo(WebhookProcessingResult.ACKNOWLEDGED_NO_BUSINESS_MUTATION);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void duplicateEventIdIsIgnoredAndNotReprocessed() {
        when(eventRepository.insertIfAbsent(eq("EVT-DUPE-123"), eq("PAYMENT.CAPTURE.COMPLETED"), nullable(String.class), eq("{}")))
                .thenReturn(0);

        var result = reconciliationService.processWebhookEvent(
                "EVT-DUPE-123", "PAYMENT.CAPTURE.COMPLETED", "CAP-1", null, "{}"
        );

        assertThat(result).isEqualTo(WebhookProcessingResult.DUPLICATE);
        verify(paymentRepository, never()).save(any());
        verify(invoiceRepository, never()).save(any());
        verify(auditEventPort, never()).recordEvent(any(), anyString(), any());
    }

    @Test
    void paymentAuthorizationVoidedUpdatesPaymentStatus() throws Exception {
        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(), UUID.randomUUID(), 1, PaymentLifecycleStatus.AUTHORIZED,
                "ORD-1", "AUTH-TARGET", null, null, 98000L, "USD"
        );
        when(paymentRepository.findByPaypalAuthorizationId("AUTH-TARGET")).thenReturn(Optional.of(payment));

        var result = reconciliationService.processWebhookEvent(
                "EVT-VOID-1",
                "PAYMENT.AUTHORIZATION.VOIDED",
                "AUTH-TARGET",
                objectMapper.readTree("{\"id\":\"AUTH-TARGET\"}"),
                "{}"
        );

        assertThat(result).isEqualTo(WebhookProcessingResult.PROCESSED);
        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.VOIDED);
        verify(paymentRepository).save(payment);
        verify(eventRepository).insertIfAbsent(anyString(), anyString(), nullable(String.class), anyString());
    }

    @Test
    void captureCompletedUsesSupplementaryDataAuthorizationId() throws Exception {
        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(), UUID.randomUUID(), 1, PaymentLifecycleStatus.AUTHORIZED,
                "ORD-1", "AUTH-SUPP", null, null, 98000L, "USD"
        );
        when(paymentRepository.findByPaypalAuthorizationId("AUTH-SUPP")).thenReturn(Optional.of(payment));

        var result = reconciliationService.processWebhookEvent(
                "EVT-CAP-1",
                "PAYMENT.CAPTURE.COMPLETED",
                "CAP-SUPP",
                objectMapper.readTree("""
                        {
                          "id": "CAP-SUPP",
                          "supplementary_data": {
                            "related_ids": {
                              "authorization_id": "AUTH-SUPP",
                              "order_id": "ORD-1"
                            }
                          }
                        }
                        """),
                "{}"
        );

        assertThat(result).isEqualTo(WebhookProcessingResult.PROCESSED);
        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.COMPLETED);
        assertThat(payment.getPaypalCaptureId()).isEqualTo("CAP-SUPP");
        verify(paymentRepository).save(payment);
    }

    @Test
    void authorizationCreatedResolvesOrderFromSupplementaryData() throws Exception {
        when(paymentRepository.findByPaypalAuthorizationId("AUTH-NEW")).thenReturn(Optional.empty());

        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(), UUID.randomUUID(), 1, PaymentLifecycleStatus.ORDER_CREATED,
                "ORD-SUPP", null, null, null, 98000L, "USD"
        );
        when(paymentRepository.findByPaypalOrderId("ORD-SUPP")).thenReturn(Optional.of(payment));

        var result = reconciliationService.processWebhookEvent(
                "EVT-AUTH-1",
                "PAYMENT.AUTHORIZATION.CREATED",
                "AUTH-NEW",
                objectMapper.readTree("""
                        {
                          "id": "AUTH-NEW",
                          "supplementary_data": {
                            "related_ids": {
                              "order_id": "ORD-SUPP"
                            }
                          }
                        }
                        """),
                "{}"
        );

        assertThat(result).isEqualTo(WebhookProcessingResult.PROCESSED);
        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.AUTHORIZED);
        assertThat(payment.getPaypalAuthorizationId()).isEqualTo("AUTH-NEW");
        verify(paymentRepository).save(payment);
    }

    @Test
    void captureDeniedMarksPaymentDeclined() throws Exception {
        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(), UUID.randomUUID(), 1, PaymentLifecycleStatus.CAPTURE_PENDING,
                "ORD-1", "AUTH-DENIED", "CAP-DENIED", null, 98000L, "USD"
        );
        when(paymentRepository.findByPaypalCaptureId("CAP-DENIED")).thenReturn(Optional.of(payment));

        var result = reconciliationService.processWebhookEvent(
                "EVT-CAP-DENIED-1",
                "PAYMENT.CAPTURE.DENIED",
                "CAP-DENIED",
                objectMapper.readTree("{\"id\":\"CAP-DENIED\"}"),
                "{}"
        );

        assertThat(result).isEqualTo(WebhookProcessingResult.PROCESSED);
        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.DECLINED);
        verify(paymentRepository).save(payment);
    }

    @Test
    void captureCompletedAlsoMarksInvoicePaid() throws Exception {
        UUID dealId = UUID.randomUUID();
        PaymentEntity payment = new PaymentEntity(
                dealId, dealId, 1, PaymentLifecycleStatus.AUTHORIZED,
                "ORD-1", "AUTH-PAID", null, null, 98000L, "USD"
        );
        InvoiceEntity invoice = new InvoiceEntity(
                UUID.randomUUID(), dealId, 1, InvoiceLifecycleStatus.SENT,
                "INV2-PAID", "001", null, 98000L, "USD"
        );
        when(paymentRepository.findByPaypalAuthorizationId("AUTH-PAID")).thenReturn(Optional.of(payment));
        when(invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.of(invoice));

        var result = reconciliationService.processWebhookEvent(
                "EVT-CAP-PAID-1",
                "PAYMENT.CAPTURE.COMPLETED",
                "CAP-PAID",
                objectMapper.readTree("""
                        {
                          "id": "CAP-PAID",
                          "supplementary_data": {
                            "related_ids": {
                              "authorization_id": "AUTH-PAID"
                            }
                          }
                        }
                        """),
                "{}"
        );

        assertThat(result).isEqualTo(WebhookProcessingResult.PROCESSED);
        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.COMPLETED);
        assertThat(invoice.getLifecycleStatus()).isEqualTo(InvoiceLifecycleStatus.PAID);
        verify(invoiceRepository).save(invoice);
    }

    @Test
    void capturePendingMarksPaymentCapturePendingForScheduledReconciliation() throws Exception {
        UUID dealId = UUID.randomUUID();
        PaymentEntity payment = new PaymentEntity(
                dealId, dealId, 1, PaymentLifecycleStatus.AUTHORIZED,
                "ORD-1", "AUTH-PENDING", null, null, 98000L, "USD"
        );
        when(paymentRepository.findByPaypalAuthorizationId("AUTH-PENDING")).thenReturn(Optional.of(payment));

        var result = reconciliationService.processWebhookEvent(
                "EVT-CAP-PENDING-1",
                "PAYMENT.CAPTURE.PENDING",
                "CAP-PENDING",
                objectMapper.readTree("""
                        {
                          "id": "CAP-PENDING",
                          "status": "PENDING",
                          "supplementary_data": {
                            "related_ids": {
                              "authorization_id": "AUTH-PENDING"
                            }
                          }
                        }
                        """),
                "{}"
        );

        assertThat(result).isEqualTo(WebhookProcessingResult.PROCESSED);
        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.CAPTURE_PENDING);
        assertThat(payment.getPaypalCaptureId()).isEqualTo("CAP-PENDING");
        verify(paymentRepository).save(payment);
    }

    @Test
    void captureCompletedClosesDealAfterAsyncCompletion() throws Exception {
        UUID dealId = UUID.randomUUID();
        PaymentEntity payment = new PaymentEntity(
                dealId, dealId, 1, PaymentLifecycleStatus.CAPTURE_PENDING,
                "ORD-1", "AUTH-CLOSE", "CAP-CLOSE", null, 98000L, "USD"
        );
        when(paymentRepository.findByPaypalAuthorizationId("AUTH-CLOSE")).thenReturn(Optional.of(payment));
        when(dealPort.getDealStatus(dealId)).thenReturn(DealStatus.DELIVERABLES_SUBMITTED);

        var result = reconciliationService.processWebhookEvent(
                "EVT-CAP-CLOSE-1",
                "PAYMENT.CAPTURE.COMPLETED",
                "CAP-CLOSE",
                objectMapper.readTree("""
                        {
                          "id": "CAP-CLOSE",
                          "status": "COMPLETED",
                          "supplementary_data": {
                            "related_ids": {
                              "authorization_id": "AUTH-CLOSE"
                            }
                          }
                        }
                        """),
                "{}"
        );

        assertThat(result).isEqualTo(WebhookProcessingResult.PROCESSED);
        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.COMPLETED);
        verify(dealPort).transitionDealStatus(dealId, DealStatus.ACCEPTED);
        verify(dealPort).transitionDealStatus(dealId, DealStatus.CLOSED);
    }

    @Test
    void authorizationCreatedTransitionsDealToInProgress() throws Exception {
        when(paymentRepository.findByPaypalAuthorizationId("AUTH-PROGRESS")).thenReturn(Optional.empty());

        UUID dealId = UUID.randomUUID();
        PaymentEntity payment = new PaymentEntity(
                dealId, dealId, 1, PaymentLifecycleStatus.ORDER_CREATED,
                "ORD-PROGRESS", null, null, null, 98000L, "USD"
        );
        when(paymentRepository.findByPaypalOrderId("ORD-PROGRESS")).thenReturn(Optional.of(payment));
        when(dealPort.getDealStatus(dealId)).thenReturn(DealStatus.SETTLEMENT_PENDING);

        var result = reconciliationService.processWebhookEvent(
                "EVT-AUTH-PROGRESS-1",
                "PAYMENT.AUTHORIZATION.CREATED",
                "AUTH-PROGRESS",
                objectMapper.readTree("""
                        {
                          "id": "AUTH-PROGRESS",
                          "status": "CREATED",
                          "supplementary_data": {
                            "related_ids": {
                              "order_id": "ORD-PROGRESS"
                            }
                          }
                        }
                        """),
                "{}"
        );

        assertThat(result).isEqualTo(WebhookProcessingResult.PROCESSED);
        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.AUTHORIZED);
        verify(dealPort).transitionDealStatus(dealId, DealStatus.IN_PROGRESS);
    }

    @Test
    void invoicePaidUpdatesInvoiceStatus() throws Exception {
        InvoiceEntity invoice = new InvoiceEntity(
                UUID.randomUUID(), UUID.randomUUID(), 1, InvoiceLifecycleStatus.SENT,
                "INV2-TARGET", "001", null, 98000L, "USD"
        );
        when(invoiceRepository.findByPaypalInvoiceId("INV2-TARGET")).thenReturn(Optional.of(invoice));

        var result = reconciliationService.processWebhookEvent(
                "EVT-INV-PAID-1",
                "INVOICING.INVOICE.PAID",
                "INV2-TARGET",
                objectMapper.readTree("{\"id\":\"INV2-TARGET\"}"),
                "{}"
        );

        assertThat(result).isEqualTo(WebhookProcessingResult.PROCESSED);
        assertThat(invoice.getLifecycleStatus()).isEqualTo(InvoiceLifecycleStatus.PAID);
        verify(invoiceRepository).save(invoice);
    }

    @Test
    void captureCompletedDoesNotOverrideVoidedPayment() throws Exception {
        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(), UUID.randomUUID(), 1, PaymentLifecycleStatus.VOIDED,
                "ORD-VOID", "AUTH-VOID", null, null, 98000L, "USD"
        );
        when(paymentRepository.findByPaypalAuthorizationId("AUTH-VOID")).thenReturn(Optional.of(payment));

        var result = reconciliationService.processWebhookEvent(
                "EVT-CAP-VOIDED",
                "PAYMENT.CAPTURE.COMPLETED",
                "CAP-LATE",
                objectMapper.readTree("""
                        {
                          "id": "CAP-LATE",
                          "supplementary_data": { "related_ids": { "authorization_id": "AUTH-VOID" } }
                        }
                        """),
                "{}"
        );

        assertThat(result).isEqualTo(WebhookProcessingResult.ACKNOWLEDGED_NO_BUSINESS_MUTATION);
        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.VOIDED);
        verify(paymentRepository, never()).save(any(PaymentEntity.class));
        verify(auditEventPort).recordEvent(any(), eq("WEBHOOK_CONFLICT_CAPTURE_COMPLETED"), any());
    }

    @Test
    void captureDeniedDoesNotOverrideCompletedPayment() throws Exception {
        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(), UUID.randomUUID(), 1, PaymentLifecycleStatus.COMPLETED,
                "ORD-DONE", "AUTH-DONE", "CAP-DONE", null, 98000L, "USD"
        );
        when(paymentRepository.findByPaypalCaptureId("CAP-DONE")).thenReturn(Optional.of(payment));

        var result = reconciliationService.processWebhookEvent(
                "EVT-CAP-DENIED-LATE",
                "PAYMENT.CAPTURE.DENIED",
                "CAP-DONE",
                objectMapper.readTree("{\"id\":\"CAP-DONE\"}"),
                "{}"
        );

        assertThat(result).isEqualTo(WebhookProcessingResult.ACKNOWLEDGED_NO_BUSINESS_MUTATION);
        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.COMPLETED);
        verify(paymentRepository, never()).save(any(PaymentEntity.class));
        verify(auditEventPort).recordEvent(any(), eq("WEBHOOK_CONFLICT_CAPTURE_DENIED"), any());
    }

    @Test
    void invoicePaidDoesNotOverrideCancelledInvoice() throws Exception {
        InvoiceEntity invoice = new InvoiceEntity(
                UUID.randomUUID(), UUID.randomUUID(), 1, InvoiceLifecycleStatus.CANCELLED,
                "INV2-CANCELLED", "001", null, 98000L, "USD"
        );
        when(invoiceRepository.findByPaypalInvoiceId("INV2-CANCELLED")).thenReturn(Optional.of(invoice));

        var result = reconciliationService.processWebhookEvent(
                "EVT-INV-PAID-LATE",
                "INVOICING.INVOICE.PAID",
                "INV2-CANCELLED",
                objectMapper.readTree("{\"id\":\"INV2-CANCELLED\"}"),
                "{}"
        );

        assertThat(result).isEqualTo(WebhookProcessingResult.ACKNOWLEDGED_NO_BUSINESS_MUTATION);
        assertThat(invoice.getLifecycleStatus()).isEqualTo(InvoiceLifecycleStatus.CANCELLED);
        verify(invoiceRepository, never()).save(any(InvoiceEntity.class));
        verify(auditEventPort).recordEvent(any(), eq("WEBHOOK_CONFLICT_INVOICE_PAID"), any());
    }

    @Test
    void invoiceCancelledDoesNotOverridePaidInvoice() throws Exception {
        InvoiceEntity invoice = new InvoiceEntity(
                UUID.randomUUID(), UUID.randomUUID(), 1, InvoiceLifecycleStatus.PAID,
                "INV2-PAID", "001", null, 98000L, "USD"
        );
        when(invoiceRepository.findByPaypalInvoiceId("INV2-PAID")).thenReturn(Optional.of(invoice));

        var result = reconciliationService.processWebhookEvent(
                "EVT-INV-CANCELLED-LATE",
                "INVOICING.INVOICE.CANCELLED",
                "INV2-PAID",
                objectMapper.readTree("{\"id\":\"INV2-PAID\"}"),
                "{}"
        );

        assertThat(result).isEqualTo(WebhookProcessingResult.ACKNOWLEDGED_NO_BUSINESS_MUTATION);
        assertThat(invoice.getLifecycleStatus()).isEqualTo(InvoiceLifecycleStatus.PAID);
        verify(invoiceRepository, never()).save(any(InvoiceEntity.class));
        verify(auditEventPort).recordEvent(any(), eq("WEBHOOK_CONFLICT_INVOICE_CANCELLED"), any());
    }

    @Test
    void capturePendingTransitionsAuthorizedPayment() throws Exception {
        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(), UUID.randomUUID(), 1, PaymentLifecycleStatus.AUTHORIZED,
                "ORD-PENDING", "AUTH-PENDING", null, null, 98000L, "USD"
        );
        when(paymentRepository.findByPaypalAuthorizationId("AUTH-PENDING")).thenReturn(Optional.of(payment));

        var result = reconciliationService.processWebhookEvent(
                "EVT-CAP-PENDING-1",
                "PAYMENT.CAPTURE.PENDING",
                "CAP-PENDING",
                objectMapper.readTree("""
                        {
                          "id": "CAP-PENDING",
                          "supplementary_data": { "related_ids": { "authorization_id": "AUTH-PENDING" } }
                        }
                        """),
                "{}"
        );

        assertThat(result).isEqualTo(WebhookProcessingResult.PROCESSED);
        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.CAPTURE_PENDING);
        verify(paymentRepository).save(payment);
    }
}
