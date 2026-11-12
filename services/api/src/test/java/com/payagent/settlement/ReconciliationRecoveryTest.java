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
import com.payagent.paypal.model.PayPalAuthorizationResponse;
import com.payagent.paypal.model.PayPalOrderResponse;
import com.payagent.settlement.persistence.PayPalEventRepository;
import com.payagent.settlement.persistence.PaymentEntity;
import com.payagent.settlement.persistence.PaymentRepository;
import com.payagent.settlement.port.AuditEventPort;
import com.payagent.agreements.AgreementRepository;
import com.payagent.settlement.port.DealPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Crash-after-capture recovery behavior. PayPal is mocked because these are
 * deterministic unit tests; PostgreSQL transaction semantics are covered by
 * the integration tests in this package.
 */
class ReconciliationRecoveryTest {

    private PayPalEventRepository eventRepository;
    private com.payagent.settlement.persistence.InvoiceRepository invoiceRepository;
    private PaymentRepository paymentRepository;
    private PayPalInvoiceService invoiceService;
    private PayPalOrderService orderService;
    private AuditEventPort auditEventPort;
    private SettlementReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        eventRepository = mock(PayPalEventRepository.class);
        invoiceRepository = mock(com.payagent.settlement.persistence.InvoiceRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        invoiceService = mock(PayPalInvoiceService.class);
        orderService = mock(PayPalOrderService.class);
        auditEventPort = mock(AuditEventPort.class);
        when(eventRepository.insertIfAbsent(anyString(), anyString(), any(), anyString())).thenReturn(1);
        when(paymentRepository.findOpenCaptureAttemptsForUpdate(any(), any())).thenReturn(List.of());
        when(invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(any())).thenReturn(Optional.empty());
        reconciliationService = new SettlementReconciliationService(
                eventRepository, invoiceRepository, paymentRepository, invoiceService, orderService, auditEventPort, mock(DealPort.class), mock(AgreementRepository.class));
    }

    @Test
    void recoveryPersistsRemoteCaptureAndClearsMarker() {
        PaymentEntity payment = attemptedPayment();
        when(paymentRepository.findOpenCaptureAttemptsForUpdate(any(), any())).thenReturn(List.of(payment));
        when(orderService.getOrder("ORD-RECOVER")).thenReturn(orderWithCapture("CAP-RECOVERED", "COMPLETED"));
        when(orderService.mapCaptureStatus("COMPLETED")).thenReturn(PaymentLifecycleStatus.COMPLETED);

        reconciliationService.recoverAuthorizedCaptures();

        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.COMPLETED);
        assertThat(payment.getPaypalCaptureId()).isEqualTo("CAP-RECOVERED");
        assertThat(payment.getCaptureAttemptedAt()).isNull();
        assertThat(payment.getCaptureResolution()).isEqualTo(CaptureAttemptService.RESOLVED);
        verify(paymentRepository).save(payment);
        verify(auditEventPort).recordEvent(any(), org.mockito.ArgumentMatchers.eq("RECOVERY_CAPTURE_COMPLETED"), any());
    }

    @Test
    void recoveryDoesNotMutateWhenPayPalLookupFails() {
        PaymentEntity payment = attemptedPayment();
        when(paymentRepository.findOpenCaptureAttemptsForUpdate(any(), any())).thenReturn(List.of(payment));
        when(orderService.getOrder("ORD-RECOVER")).thenThrow(new RuntimeException("paypal timeout"));

        reconciliationService.recoverAuthorizedCaptures();

        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.AUTHORIZED);
        assertThat(payment.getCaptureAttemptedAt()).isNotNull();
        verify(paymentRepository, never()).save(payment);
    }

    @Test
    void recoveryKeepsAttemptPendingWhenAuthorizationStillAuthorized() {
        PaymentEntity payment = attemptedPayment();
        when(paymentRepository.findOpenCaptureAttemptsForUpdate(any(), any())).thenReturn(List.of(payment));
        when(orderService.getOrder("ORD-RECOVER")).thenReturn(orderWithoutCapture());
        when(orderService.getAuthorization("AUTH-RECOVER"))
                .thenReturn(new PayPalAuthorizationResponse("AUTH-RECOVER", "CREATED",
                        new PayPalAuthorizationResponse.Amount("USD", "980.00"), null));
        when(orderService.mapAuthorizationStatus("CREATED")).thenReturn(PaymentLifecycleStatus.AUTHORIZED);

        reconciliationService.recoverAuthorizedCaptures();

        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.AUTHORIZED);
        assertThat(payment.getCaptureAttemptedAt()).isNotNull();
        assertThat(payment.getCaptureResolution()).isEqualTo(CaptureAttemptService.PENDING_RECONCILIATION);
        verify(paymentRepository).save(payment);
    }

    @Test
    void recoveryNeverClearsOldAttemptWhenAuthorizationStillAuthorized() {
        PaymentEntity payment = attemptedPayment();
        payment.setCaptureAttemptedAt(Instant.now().minusSeconds(3600));
        when(paymentRepository.findOpenCaptureAttemptsForUpdate(any(), any())).thenReturn(List.of(payment));
        when(orderService.getOrder("ORD-RECOVER")).thenReturn(orderWithoutCapture());
        when(orderService.getAuthorization("AUTH-RECOVER"))
                .thenReturn(new PayPalAuthorizationResponse("AUTH-RECOVER", "CREATED",
                        new PayPalAuthorizationResponse.Amount("USD", "980.00"), null));
        when(orderService.mapAuthorizationStatus("CREATED")).thenReturn(PaymentLifecycleStatus.AUTHORIZED);

        reconciliationService.recoverAuthorizedCaptures();

        assertThat(payment.getLifecycleStatus()).isEqualTo(PaymentLifecycleStatus.AUTHORIZED);
        // An unresolved, possibly-in-flight remote capture must NOT unblock a blind second capture.
        assertThat(payment.getCaptureAttemptedAt()).isNotNull();
        assertThat(payment.getCaptureResolution()).isEqualTo(CaptureAttemptService.PENDING_RECONCILIATION);
        verify(paymentRepository).save(payment);
    }

    @Test
    void recoveryIsNoOpWhenCaptureAlreadyPersisted() {
        PaymentEntity payment = attemptedPayment();
        payment.setCaptureAttemptedAt(null);
        payment.setPaypalCaptureId("CAP-DONE");
        payment.setLifecycleStatus(PaymentLifecycleStatus.COMPLETED);
        when(paymentRepository.findOpenCaptureAttemptsForUpdate(any(), any())).thenReturn(List.of());

        reconciliationService.recoverAuthorizedCaptures();

        verify(orderService, never()).getOrder(anyString());
        verify(paymentRepository, never()).save(payment);
    }

    private PaymentEntity attemptedPayment() {
        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(), UUID.randomUUID(), 1, PaymentLifecycleStatus.AUTHORIZED,
                "ORD-RECOVER", "AUTH-RECOVER", null, Instant.now().plusSeconds(86400), 98000L, "USD");
        payment.setCaptureAttemptedAt(Instant.now());
        payment.setCaptureResolution(CaptureAttemptService.OPEN);
        return payment;
    }

    private PayPalOrderResponse orderWithoutCapture() {
        return new PayPalOrderResponse("ORD-RECOVER", "APPROVED", "AUTHORIZE",
                List.of(new PayPalOrderResponse.PurchaseUnit("ref", "INV",
                        new PayPalOrderResponse.Amount("USD", "980.00"), null)),
                List.of());
    }

    private PayPalOrderResponse orderWithCapture(String captureId, String captureStatus) {
        var captures = List.of(new PayPalOrderResponse.CaptureDetail(captureId, captureStatus,
                new PayPalOrderResponse.Amount("USD", "980.00")));
        return new PayPalOrderResponse("ORD-RECOVER", "COMPLETED", "AUTHORIZE",
                List.of(new PayPalOrderResponse.PurchaseUnit("ref", "INV",
                        new PayPalOrderResponse.Amount("USD", "980.00"),
                        new PayPalOrderResponse.Payments(null, captures))),
                List.of());
    }
}
