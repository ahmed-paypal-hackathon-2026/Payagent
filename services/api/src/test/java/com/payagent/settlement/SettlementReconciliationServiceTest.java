package com.payagent.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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

        reconciliationService = new SettlementReconciliationService(
                eventRepository,
                invoiceRepository,
                paymentRepository,
                invoiceService,
                orderService,
                auditEventPort
        );
        objectMapper = new ObjectMapper();
    }

    @Test
    void duplicateEventIdIsIgnoredAndNotReprocessed() {
        when(eventRepository.existsByEventId("EVT-DUPE-123")).thenReturn(true);

        boolean processed = reconciliationService.processWebhookEvent(
                "EVT-DUPE-123", "PAYMENT.CAPTURE.COMPLETED", "CAP-1", null, "{}"
        );

        assertThat(processed).isFalse();
        verify(eventRepository, never()).save(any(PayPalEventEntity.class));
    }

    @Test
    void paymentAuthorizationVoidedUpdatesPaymentStatus() throws Exception {
        when(eventRepository.existsByEventId("EVT-VOID-1")).thenReturn(false);

        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(), UUID.randomUUID(), 1, PaymentLifecycleStatus.AUTHORIZED,
                "ORD-1", "AUTH-TARGET", null, null, 98000L, "USD"
        );
        when(paymentRepository.findByPaypalAuthorizationId("AUTH-TARGET")).thenReturn(Optional.of(payment));

        boolean processed = reconciliationService.processWebhookEvent(
                "EVT-VOID-1",
                "PAYMENT.AUTHORIZATION.VOIDED",
                "AUTH-TARGET",
                objectMapper.readTree("{\"id\":\"AUTH-TARGET\"}"),
                "{}"
        );

        assertThat(processed).isTrue();
        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.VOIDED);
        verify(paymentRepository).save(payment);
        verify(eventRepository).save(any(PayPalEventEntity.class));
    }

    @Test
    void captureCompletedUsesSupplementaryDataAuthorizationId() throws Exception {
        when(eventRepository.existsByEventId("EVT-CAP-1")).thenReturn(false);

        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(), UUID.randomUUID(), 1, PaymentLifecycleStatus.AUTHORIZED,
                "ORD-1", "AUTH-SUPP", null, null, 98000L, "USD"
        );
        when(paymentRepository.findByPaypalAuthorizationId("AUTH-SUPP")).thenReturn(Optional.of(payment));

        boolean processed = reconciliationService.processWebhookEvent(
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

        assertThat(processed).isTrue();
        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.COMPLETED);
        assertThat(payment.getPaypalCaptureId()).isEqualTo("CAP-SUPP");
        verify(paymentRepository).save(payment);
    }

    @Test
    void authorizationCreatedResolvesOrderFromSupplementaryData() throws Exception {
        when(eventRepository.existsByEventId("EVT-AUTH-1")).thenReturn(false);
        when(paymentRepository.findByPaypalAuthorizationId("AUTH-NEW")).thenReturn(Optional.empty());

        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(), UUID.randomUUID(), 1, PaymentLifecycleStatus.ORDER_CREATED,
                "ORD-SUPP", null, null, null, 98000L, "USD"
        );
        when(paymentRepository.findByPaypalOrderId("ORD-SUPP")).thenReturn(Optional.of(payment));

        boolean processed = reconciliationService.processWebhookEvent(
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

        assertThat(processed).isTrue();
        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.AUTHORIZED);
        assertThat(payment.getPaypalAuthorizationId()).isEqualTo("AUTH-NEW");
        verify(paymentRepository).save(payment);
    }

    @Test
    void captureDeniedMarksPaymentDeclined() throws Exception {
        when(eventRepository.existsByEventId("EVT-CAP-DENIED-1")).thenReturn(false);

        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(), UUID.randomUUID(), 1, PaymentLifecycleStatus.CAPTURE_PENDING,
                "ORD-1", "AUTH-DENIED", "CAP-DENIED", null, 98000L, "USD"
        );
        when(paymentRepository.findByPaypalCaptureId("CAP-DENIED")).thenReturn(Optional.of(payment));

        boolean processed = reconciliationService.processWebhookEvent(
                "EVT-CAP-DENIED-1",
                "PAYMENT.CAPTURE.DENIED",
                "CAP-DENIED",
                objectMapper.readTree("{\"id\":\"CAP-DENIED\"}"),
                "{}"
        );

        assertThat(processed).isTrue();
        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.DECLINED);
        verify(paymentRepository).save(payment);
    }

    @Test
    void captureCompletedAlsoMarksInvoicePaid() throws Exception {
        when(eventRepository.existsByEventId("EVT-CAP-PAID-1")).thenReturn(false);

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

        boolean processed = reconciliationService.processWebhookEvent(
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

        assertThat(processed).isTrue();
        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.COMPLETED);
        assertThat(invoice.getLifecycleStatus()).isEqualTo(InvoiceLifecycleStatus.PAID);
        verify(invoiceRepository).save(invoice);
    }

    @Test
    void invoicePaidUpdatesInvoiceStatus() throws Exception {
        when(eventRepository.existsByEventId("EVT-INV-PAID-1")).thenReturn(false);

        InvoiceEntity invoice = new InvoiceEntity(
                UUID.randomUUID(), UUID.randomUUID(), 1, InvoiceLifecycleStatus.SENT,
                "INV2-TARGET", "001", null, 98000L, "USD"
        );
        when(invoiceRepository.findByPaypalInvoiceId("INV2-TARGET")).thenReturn(Optional.of(invoice));

        boolean processed = reconciliationService.processWebhookEvent(
                "EVT-INV-PAID-1",
                "INVOICING.INVOICE.PAID",
                "INV2-TARGET",
                objectMapper.readTree("{\"id\":\"INV2-TARGET\"}"),
                "{}"
        );

        assertThat(processed).isTrue();
        assertThat(invoice.getLifecycleStatus()).isEqualTo(InvoiceLifecycleStatus.PAID);
        verify(invoiceRepository).save(invoice);
    }
}
