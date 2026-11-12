package com.payagent.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.payagent.common.contracts.AgreementSnapshotDTO;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.InvoiceLifecycleStatus;
import com.payagent.common.contracts.PaymentLifecycleStatus;
import com.payagent.common.contracts.SettlementStateDTO;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.paypal.PayPalInvoiceService;
import com.payagent.paypal.PayPalOrderService;
import com.payagent.paypal.model.AuthorizeOrderResult;
import com.payagent.paypal.model.CreateInvoiceCommand;
import com.payagent.paypal.model.CreateOrderCommand;
import com.payagent.paypal.model.PayPalCaptureResponse;
import com.payagent.paypal.model.PayPalInvoiceResponse;
import com.payagent.paypal.model.PayPalOrderResponse;
import com.payagent.settlement.exception.SettlementConflictException;
import com.payagent.settlement.exception.SettlementForbiddenException;
import com.payagent.settlement.persistence.InvoiceEntity;
import com.payagent.settlement.persistence.InvoiceRepository;
import com.payagent.settlement.persistence.PaymentEntity;
import com.payagent.settlement.persistence.PaymentRepository;
import com.payagent.settlement.persistence.PayPalEventRepository;
import com.payagent.settlement.port.AuditEventPort;
import com.payagent.settlement.port.DealPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class SettlementServiceTest {

    private PayPalInvoiceService invoiceService;
    private PayPalOrderService orderService;
    private InvoiceRepository invoiceRepository;
    private PaymentRepository paymentRepository;
    private DealPort dealPort;
    private AuditEventPort auditEventPort;
    private CaptureAttemptService captureAttemptService;
    private PayPalEventRepository payPalEventRepository;
    private SettlementService settlementService;

    private final UUID dealId = UUID.randomUUID();
    private final String buyerEmail = "buyer@example.com";
    private final String sellerEmail = "seller@example.com";
    private final String termsHash = "11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff";

    @BeforeEach
    void setUp() {
        invoiceService = mock(PayPalInvoiceService.class);
        orderService = mock(PayPalOrderService.class);
        invoiceRepository = mock(InvoiceRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        dealPort = mock(DealPort.class);
        auditEventPort = mock(AuditEventPort.class);
        captureAttemptService = mock(CaptureAttemptService.class);
        payPalEventRepository = mock(PayPalEventRepository.class);

        settlementService = new SettlementService(
                invoiceService,
                orderService,
                invoiceRepository,
                paymentRepository,
                dealPort,
                auditEventPort,
                captureAttemptService,
                payPalEventRepository
        );

        when(dealPort.getBuyerEmail(dealId)).thenReturn(buyerEmail);
        when(dealPort.getSellerEmail(dealId)).thenReturn(sellerEmail);
        when(dealPort.lockDealAndGetStatus(dealId)).thenReturn(DealStatus.DELIVERABLES_SUBMITTED);
        when(dealPort.hasOpenDispute(dealId)).thenReturn(false);
        when(orderService.mapCaptureStatus("COMPLETED")).thenReturn(PaymentLifecycleStatus.COMPLETED);
    }

    @Test
    void initiateSettlementSucceedsWhenDealIsAgreed() {
        when(dealPort.getDealStatus(dealId)).thenReturn(DealStatus.AGREED);

        StructuredTermsDTO terms = new StructuredTermsDTO(
                "Autonomous Bot Development",
                List.of("Module A", "Module B"),
                98000L,
                "USD",
                7,
                2,
                "Passes test suite"
        );
        AgreementSnapshotDTO agreement = new AgreementSnapshotDTO(
                UUID.randomUUID().toString(),
                dealId.toString(),
                1,
                terms,
                termsHash,
                "2026-10-25T12:00:00Z",
                "2026-10-25T12:05:00Z"
        );
        when(dealPort.getAgreedAgreement(dealId)).thenReturn(agreement);

        PayPalInvoiceResponse invoiceResp = new PayPalInvoiceResponse(
                "INV2-XYZ-999",
                "DRAFT",
                new PayPalInvoiceResponse.Detail("INV-001", "2026-10-25", "USD", "termsHash", "DUE_ON_RECEIPT", null),
                new PayPalInvoiceResponse.Amount("USD", "980.00"),
                List.of(new PayPalInvoiceResponse.Link("payer-view", "https://paypal.sandbox/view/INV2-XYZ-999", "GET"))
        );
        when(invoiceService.createDraftInvoice(any(CreateInvoiceCommand.class), anyString())).thenReturn(invoiceResp);

        PayPalOrderResponse orderResp = new PayPalOrderResponse(
                "ORD-TEST-123",
                "CREATED",
                "AUTHORIZE",
                null,
                null
        );
        when(orderService.createOrder(any(CreateOrderCommand.class), anyString())).thenReturn(orderResp);
        when(invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.empty());
        when(paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.empty());

        SettlementStateDTO state = settlementService.initiateSettlement(dealId, "idemp-key-1", buyerEmail);

        assertThat(state.dealId()).isEqualTo(dealId.toString());
        assertThat(state.invoiceStatus()).isEqualTo(InvoiceLifecycleStatus.SENT);
        assertThat(state.paypalInvoiceId()).isEqualTo("INV2-XYZ-999");
        assertThat(state.paymentStatus()).isEqualTo(PaymentLifecycleStatus.ORDER_CREATED);
        assertThat(state.paypalOrderId()).isEqualTo("ORD-TEST-123");
        assertThat(state.amountMinorUnits()).isEqualTo(98000L);

        verify(invoiceService).sendInvoice(eq("INV2-XYZ-999"), anyString());
        verify(dealPort).transitionDealStatus(dealId, DealStatus.SETTLEMENT_PENDING);
        verify(invoiceRepository).save(any(InvoiceEntity.class));
        verify(paymentRepository).save(any(PaymentEntity.class));
    }

    @Test
    void initiateSettlementReusesExistingSentInvoice() {
        when(dealPort.getDealStatus(dealId)).thenReturn(DealStatus.AGREED);

        InvoiceEntity existingInvoice = new InvoiceEntity(
                UUID.randomUUID(),
                dealId,
                1,
                InvoiceLifecycleStatus.SENT,
                "INV2-EXISTING",
                "INV-EX",
                "https://paypal.sandbox/view/existing",
                98000L,
                "USD"
        );
        PaymentEntity existingPayment = new PaymentEntity(
                UUID.randomUUID(),
                dealId,
                1,
                PaymentLifecycleStatus.ORDER_CREATED,
                "ORD-EXISTING",
                null,
                null,
                null,
                98000L,
                "USD"
        );

        when(invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.of(existingInvoice));
        when(paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.of(existingPayment));

        SettlementStateDTO state = settlementService.initiateSettlement(dealId, "idemp-key-reuse", buyerEmail);

        assertThat(state.paypalInvoiceId()).isEqualTo("INV2-EXISTING");
        assertThat(state.paypalOrderId()).isEqualTo("ORD-EXISTING");
        verify(invoiceService, never()).createDraftInvoice(any(), any());
        verify(invoiceService, never()).sendInvoice(any(), any());
    }

    @Test
    void initiateSettlementRejectsNonAgreedDealStatus() {
        when(dealPort.getDealStatus(dealId)).thenReturn(DealStatus.ACTIVE_NEGOTIATION);

        assertThatThrownBy(() -> settlementService.initiateSettlement(dealId, "key", buyerEmail))
                .isInstanceOf(SettlementConflictException.class)
                .hasMessageContaining("required AGREED");

        verify(invoiceService, never()).createDraftInvoice(any(), any());
    }

    @Test
    void authorizeSettlementStoresAuthIdAndTransitionsToInProgress() {
        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(),
                dealId,
                1,
                PaymentLifecycleStatus.ORDER_CREATED,
                "ORD-TEST-123",
                null,
                null,
                null,
                98000L,
                "USD"
        );
        when(paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.of(payment));
        when(orderService.authorizeOrder(eq("ORD-TEST-123"), anyString()))
                .thenReturn(new AuthorizeOrderResult("ORD-TEST-123", "AUTH-999-XYZ", "CREATED", "2026-11-23T12:00:00Z"));

        SettlementStateDTO state = settlementService.authorizeSettlement(dealId, "ORD-TEST-123", "idemp-key-2", buyerEmail);

        assertThat(state.paymentStatus()).isEqualTo(PaymentLifecycleStatus.AUTHORIZED);
        assertThat(state.paypalAuthorizationId()).isEqualTo("AUTH-999-XYZ");
        assertThat(state.authorizationExpiresAt()).contains("2026-11-23");

        verify(dealPort).transitionDealStatus(dealId, DealStatus.IN_PROGRESS);
        verify(paymentRepository).save(payment);
    }

    @Test
    void authorizeSettlementIsIdempotentWhenAlreadyAuthorized() {
        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(),
                dealId,
                1,
                PaymentLifecycleStatus.AUTHORIZED,
                "ORD-TEST-123",
                "AUTH-EXISTING",
                null,
                Instant.now().plusSeconds(86400),
                98000L,
                "USD"
        );
        when(paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.of(payment));

        SettlementStateDTO state = settlementService.authorizeSettlement(dealId, "ORD-TEST-123", "idemp-key-replay", buyerEmail);

        assertThat(state.paymentStatus()).isEqualTo(PaymentLifecycleStatus.AUTHORIZED);
        assertThat(state.paypalAuthorizationId()).isEqualTo("AUTH-EXISTING");
        verify(orderService, never()).authorizeOrder(anyString(), anyString());
    }

    @Test
    void acceptDeliverablesBlocksCaptureWhenDealIsDisputed() {
        when(dealPort.isDealDisputed(dealId)).thenReturn(true);

        assertThatThrownBy(() -> settlementService.acceptDeliverables(dealId, 1, "key", buyerEmail))
                .isInstanceOf(SettlementConflictException.class)
                .hasMessageContaining("Capture blocked: deal is in DISPUTED status");

        verify(orderService, never()).captureAuthorization(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void acceptDeliverablesRejectsNonBuyerCaller() {
        org.mockito.Mockito.doThrow(new SettlementForbiddenException("Caller is not authorized BUYER"))
                .when(dealPort).assertBuyer(dealId, sellerEmail);

        assertThatThrownBy(() -> settlementService.acceptDeliverables(dealId, 1, "key", sellerEmail))
                .isInstanceOf(SettlementForbiddenException.class);

        verify(orderService, never()).captureAuthorization(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void acceptDeliverablesCapturesFundsRecordsPaymentAndClosesDeal() {
        when(dealPort.isDealDisputed(dealId)).thenReturn(false);

        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(),
                dealId,
                1,
                PaymentLifecycleStatus.AUTHORIZED,
                "ORD-123",
                "AUTH-777",
                null,
                Instant.now().plusSeconds(86400),
                98000L,
                "USD"
        );
        InvoiceEntity invoice = new InvoiceEntity(
                UUID.randomUUID(),
                dealId,
                1,
                InvoiceLifecycleStatus.SENT,
                "INV2-888",
                "INV-01",
                "https://paypal.sandbox/view",
                98000L,
                "USD"
        );

        when(paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.of(payment));
        when(invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.of(invoice));
        when(orderService.captureAuthorization(eq("AUTH-777"), eq(98000L), eq("USD"), anyString()))
                .thenReturn(new PayPalCaptureResponse("CAP-333", "COMPLETED", new PayPalCaptureResponse.Amount("USD", "980.00")));

        SettlementStateDTO state = settlementService.acceptDeliverables(dealId, 1, "idemp-key-3", buyerEmail);

        assertThat(state.paymentStatus()).isEqualTo(PaymentLifecycleStatus.COMPLETED);
        assertThat(state.paypalCaptureId()).isEqualTo("CAP-333");
        assertThat(state.invoiceStatus()).isEqualTo(InvoiceLifecycleStatus.PAID);

        verify(invoiceService).recordPayment(eq("INV2-888"), eq(98000L), eq("USD"), anyString(), anyString());
        verify(dealPort).transitionDealStatus(dealId, DealStatus.ACCEPTED);
        verify(dealPort).transitionDealStatus(dealId, DealStatus.CLOSED);
        verify(captureAttemptService).markCaptureAttempted(eq(payment.getId()), anyString());
    }

    @Test
    void acceptDeliverablesPreventsDoubleCaptureIfAlreadyCompleted() {
        when(dealPort.isDealDisputed(dealId)).thenReturn(false);

        PaymentEntity completedPayment = new PaymentEntity(
                UUID.randomUUID(),
                dealId,
                1,
                PaymentLifecycleStatus.COMPLETED,
                "ORD-123",
                "AUTH-777",
                "CAP-ALREADY-DONE",
                Instant.now().plusSeconds(86400),
                98000L,
                "USD"
        );
        InvoiceEntity paidInvoice = new InvoiceEntity(
                UUID.randomUUID(),
                dealId,
                1,
                InvoiceLifecycleStatus.PAID,
                "INV2-888",
                "INV-01",
                "https://paypal.sandbox/view",
                98000L,
                "USD"
        );

        when(paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.of(completedPayment));
        when(invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.of(paidInvoice));

        SettlementStateDTO state = settlementService.acceptDeliverables(dealId, 1, "idemp-key-replay", buyerEmail);

        assertThat(state.paymentStatus()).isEqualTo(PaymentLifecycleStatus.COMPLETED);
        assertThat(state.paypalCaptureId()).isEqualTo("CAP-ALREADY-DONE");

        // Asserts NO secondary call to PayPal capture or invoice payment was made
        verify(orderService, never()).captureAuthorization(anyString(), anyLong(), anyString(), anyString());
        verify(invoiceService, never()).recordPayment(anyString(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void acceptDeliverablesRechecksDisputeAfterLockingDeal() {
        when(dealPort.lockDealAndGetStatus(dealId)).thenReturn(DealStatus.DISPUTED);

        assertThatThrownBy(() -> settlementService.acceptDeliverables(dealId, 1, "key", buyerEmail))
                .isInstanceOf(SettlementConflictException.class)
                .hasMessageContaining("Capture blocked: deal is in DISPUTED status");

        verify(orderService, never()).captureAuthorization(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void acceptDeliverablesRequiresDeliverablesSubmittedStatus() {
        when(dealPort.lockDealAndGetStatus(dealId)).thenReturn(DealStatus.IN_PROGRESS);

        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(),
                dealId,
                1,
                PaymentLifecycleStatus.AUTHORIZED,
                "ORD-123",
                "AUTH-777",
                null,
                Instant.now().plusSeconds(86400),
                98000L,
                "USD"
        );
        when(paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> settlementService.acceptDeliverables(dealId, 1, "key", buyerEmail))
                .isInstanceOf(SettlementConflictException.class)
                .hasMessageContaining("required DELIVERABLES_SUBMITTED");

        verify(orderService, never()).captureAuthorization(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void acceptDeliverablesLeavesDealOpenWhenCaptureIsPending() {
        when(dealPort.isDealDisputed(dealId)).thenReturn(false);
        when(orderService.mapCaptureStatus("PENDING")).thenReturn(PaymentLifecycleStatus.CAPTURE_PENDING);

        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(),
                dealId,
                1,
                PaymentLifecycleStatus.AUTHORIZED,
                "ORD-123",
                "AUTH-777",
                null,
                Instant.now().plusSeconds(86400),
                98000L,
                "USD"
        );
        InvoiceEntity invoice = new InvoiceEntity(
                UUID.randomUUID(),
                dealId,
                1,
                InvoiceLifecycleStatus.SENT,
                "INV2-888",
                "INV-01",
                "https://paypal.sandbox/view",
                98000L,
                "USD"
        );
        when(paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.of(payment));
        when(invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.of(invoice));
        when(orderService.captureAuthorization(eq("AUTH-777"), eq(98000L), eq("USD"), anyString()))
                .thenReturn(new PayPalCaptureResponse("CAP-PENDING", "PENDING", new PayPalCaptureResponse.Amount("USD", "980.00")));

        SettlementStateDTO state = settlementService.acceptDeliverables(dealId, 1, "idemp-key-pending", buyerEmail);

        assertThat(state.paymentStatus()).isEqualTo(PaymentLifecycleStatus.CAPTURE_PENDING);
        assertThat(state.invoiceStatus()).isEqualTo(InvoiceLifecycleStatus.SENT);
        verify(invoiceService, never()).recordPayment(anyString(), anyLong(), anyString(), anyString(), anyString());
        verify(dealPort, never()).transitionDealStatus(dealId, DealStatus.CLOSED);
    }

    @Test
    void voidSettlementRejectsVoidAfterCaptureCompleted() {
        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(),
                dealId,
                1,
                PaymentLifecycleStatus.COMPLETED,
                "ORD-123",
                "AUTH-777",
                "CAP-DONE",
                Instant.now().plusSeconds(86400),
                98000L,
                "USD"
        );
        when(paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> settlementService.voidSettlement(dealId, "cancel", "key", buyerEmail))
                .isInstanceOf(SettlementConflictException.class)
                .hasMessageContaining("already captured");

        verify(orderService, never()).voidAuthorization(anyString(), anyString());
    }

    @Test
    void voidSettlementVoidsAuthorizationAndCancelsInvoice() {
        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(),
                dealId,
                1,
                PaymentLifecycleStatus.AUTHORIZED,
                "ORD-123",
                "AUTH-VOID-ME",
                null,
                Instant.now().plusSeconds(86400),
                98000L,
                "USD"
        );
        InvoiceEntity invoice = new InvoiceEntity(
                UUID.randomUUID(),
                dealId,
                1,
                InvoiceLifecycleStatus.SENT,
                "INV2-CANCEL-ME",
                "INV-01",
                null,
                98000L,
                "USD"
        );

        when(paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.of(payment));
        when(invoiceRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.of(invoice));

        SettlementStateDTO state = settlementService.voidSettlement(dealId, "Mutual cancel", "key-void", buyerEmail);

        assertThat(state.paymentStatus()).isEqualTo(PaymentLifecycleStatus.VOIDED);
        assertThat(state.invoiceStatus()).isEqualTo(InvoiceLifecycleStatus.CANCELLED);

        verify(orderService).voidAuthorization(eq("AUTH-VOID-ME"), anyString());
        verify(invoiceService).cancelInvoice(eq("INV2-CANCEL-ME"), eq("Mutual cancel"), anyString());
    }

    @Test
    void getSettlementStateRejectsCallerWhoIsNotADealParty() {
        doThrow(new SettlementForbiddenException("Caller is not a party to this deal"))
                .when(dealPort).assertParty(dealId, "intruder@example.com");

        assertThatThrownBy(() -> settlementService.getSettlementState(dealId, "intruder@example.com"))
                .isInstanceOf(SettlementForbiddenException.class);

        verifyNoInteractions(invoiceRepository, paymentRepository);
    }

    @Test
    void getSettlementStateAllowsDealPartyToReadState() {
        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(),
                dealId,
                1,
                PaymentLifecycleStatus.AUTHORIZED,
                "ORD-READ",
                "AUTH-READ",
                null,
                null,
                98000L,
                "USD"
        );
        when(paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.of(payment));

        SettlementStateDTO state = settlementService.getSettlementState(dealId, buyerEmail);

        verify(dealPort).assertParty(dealId, buyerEmail);
        assertThat(state.paymentStatus()).isEqualTo(PaymentLifecycleStatus.AUTHORIZED);
        assertThat(state.paypalAuthorizationId()).isEqualTo("AUTH-READ");
    }

    @Test
    void acceptDeliverablesBlocksWhenCaptureAttemptIsAlreadyOpen() {
        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(),
                dealId,
                1,
                PaymentLifecycleStatus.AUTHORIZED,
                "ORD-OPEN",
                "AUTH-OPEN",
                null,
                Instant.now().plusSeconds(86400),
                98000L,
                "USD"
        );
        payment.setCaptureAttemptedAt(Instant.now().minusSeconds(30));
        payment.setCaptureResolution(CaptureAttemptService.PENDING_RECONCILIATION);
        when(paymentRepository.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> settlementService.acceptDeliverables(dealId, 1, "key-open", buyerEmail))
                .isInstanceOf(SettlementConflictException.class)
                .hasMessageContaining("awaiting reconciliation");

        verify(orderService, never()).captureAuthorization(anyString(), anyLong(), anyString(), anyString());
        verify(captureAttemptService, never()).markCaptureAttempted(any(), anyString());
    }
}
