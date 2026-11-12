package com.payagent.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
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

        when(eventRepository.insertIfAbsent(anyString(), anyString(), nullable(String.class), anyString())).thenReturn(1);

        reconciliationService = new SettlementReconciliationService(
                eventRepository,
                invoiceRepository,
                paymentRepository,
                invoiceService,
                orderService,
                auditEventPort,
                dealPort
        );
        objectMapper = new ObjectMapper();
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
        when(eventRepository.existsByEventId("EVT-CAP-PENDING-1")).thenReturn(false);

        UUID dealId = UUID.randomUUID();
        PaymentEntity payment = new PaymentEntity(
                dealId, dealId, 1, PaymentLifecycleStatus.AUTHORIZED,
                "ORD-1", "AUTH-PENDING", null, null, 98000L, "USD"
        );
        when(paymentRepository.findByPaypalAuthorizationId("AUTH-PENDING")).thenReturn(Optional.of(payment));

        boolean processed = reconciliationService.processWebhookEvent(
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

        assertThat(processed).isTrue();
        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.CAPTURE_PENDING);
        assertThat(payment.getPaypalCaptureId()).isEqualTo("CAP-PENDING");
        verify(paymentRepository).save(payment);
    }

    @Test
    void captureCompletedClosesDealAfterAsyncCompletion() throws Exception {
        when(eventRepository.existsByEventId("EVT-CAP-CLOSE-1")).thenReturn(false);

        UUID dealId = UUID.randomUUID();
        PaymentEntity payment = new PaymentEntity(
                dealId, dealId, 1, PaymentLifecycleStatus.CAPTURE_PENDING,
                "ORD-1", "AUTH-CLOSE", "CAP-CLOSE", null, 98000L, "USD"
        );
        when(paymentRepository.findByPaypalAuthorizationId("AUTH-CLOSE")).thenReturn(Optional.of(payment));
        when(dealPort.getDealStatus(dealId)).thenReturn(DealStatus.DELIVERABLES_SUBMITTED);

        boolean processed = reconciliationService.processWebhookEvent(
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

        assertThat(processed).isTrue();
        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.COMPLETED);
        verify(dealPort).transitionDealStatus(dealId, DealStatus.ACCEPTED);
        verify(dealPort).transitionDealStatus(dealId, DealStatus.CLOSED);
    }

    @Test
    void authorizationCreatedTransitionsDealToInProgress() throws Exception {
        when(eventRepository.existsByEventId("EVT-AUTH-PROGRESS-1")).thenReturn(false);
        when(paymentRepository.findByPaypalAuthorizationId("AUTH-PROGRESS")).thenReturn(Optional.empty());

        UUID dealId = UUID.randomUUID();
        PaymentEntity payment = new PaymentEntity(
                dealId, dealId, 1, PaymentLifecycleStatus.ORDER_CREATED,
                "ORD-PROGRESS", null, null, null, 98000L, "USD"
        );
        when(paymentRepository.findByPaypalOrderId("ORD-PROGRESS")).thenReturn(Optional.of(payment));
        when(dealPort.getDealStatus(dealId)).thenReturn(DealStatus.SETTLEMENT_PENDING);

        boolean processed = reconciliationService.processWebhookEvent(
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

        assertThat(processed).isTrue();
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
