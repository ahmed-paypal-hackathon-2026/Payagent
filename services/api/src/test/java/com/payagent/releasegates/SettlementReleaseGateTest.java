package com.payagent.releasegates;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.common.contracts.*;
import com.payagent.paypal.*;
import com.payagent.settlement.*;
import com.payagent.settlement.exception.*;
import com.payagent.settlement.persistence.*;
import com.payagent.settlement.port.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettlementReleaseGateTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void tst07_invalidWebhookNeverReachesReconciliation() throws Exception {
        PayPalWebhookVerifier verifier = mock(PayPalWebhookVerifier.class);
        SettlementReconciliationService reconciliation = mock(SettlementReconciliationService.class);
        when(verifier.verifySignature(any(), any(), any(), any(), any(), any())).thenReturn(false);
        var mvc = MockMvcBuilders.standaloneSetup(new PayPalWebhookController(verifier, reconciliation, mapper)).build();
        mvc.perform(post("/api/v1/paypal/webhooks").contentType(MediaType.APPLICATION_JSON)
                .header("PAYPAL-TRANSMISSION-SIG", "forged")
                .content("{\"id\":\"WH-FORGED\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"resource\":{\"id\":\"CAP-FORGED\"}}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reconciliation);
    }

    @Test
    void tst08_duplicateFixtureAppliesStateMutationOnlyOnce() throws Exception {
        var fixture = mapper.readTree(Path.of("../../shared/fixtures/08-duplicate-webhook-event.json").toFile());
        String eventId = fixture.get("eventId").asText();
        String authId = fixture.get("resourceId").asText();
        PayPalEventRepository events = mock(PayPalEventRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        AuditEventPort audit = mock(AuditEventPort.class);
        PaymentEntity payment = new PaymentEntity(UUID.randomUUID(), UUID.randomUUID(), 1,
                PaymentLifecycleStatus.ORDER_CREATED, "ORDER-SYNTH", authId, null, null, 98000L, "USD");
        when(events.existsByEventId(eventId)).thenReturn(false, true);
        when(payments.findByPaypalAuthorizationId(authId)).thenReturn(Optional.of(payment));
        SettlementReconciliationService service = new SettlementReconciliationService(events, invoices, payments,
                mock(PayPalInvoiceService.class), mock(PayPalOrderService.class), audit, mock(com.payagent.settlement.port.DealPort.class));
        String payload = fixture.get("payload").toString();
        assertTrue(service.processWebhookEvent(eventId, fixture.get("eventType").asText(), authId, fixture.at("/payload/resource"), payload));
        assertFalse(service.processWebhookEvent(eventId, fixture.get("eventType").asText(), authId, fixture.at("/payload/resource"), payload));
        assertEquals(PaymentLifecycleStatus.AUTHORIZED, payment.getLifecycleStatus());
        verify(events, times(1)).save(any(PayPalEventEntity.class));
        verify(payments, times(1)).save(payment);
        verify(audit, times(1)).recordEvent(eq(payment.getDealId()), eq("WEBHOOK_RECONCILED_AUTHORIZED"), any());
    }

    @Test
    void tst09_replayUsesStoredResponseAndChangedPayloadCannotExecute() {
        IdempotencyKeyRepository repository = mock(IdempotencyKeyRepository.class);
        Map<String, IdempotencyKeyEntity> records = new HashMap<>();
        when(repository.findByIdempotencyKeyAndRoute(anyString(), anyString()))
                .thenAnswer(i -> Optional.ofNullable(records.get(i.getArgument(0) + ":" + i.getArgument(1))));
        when(repository.save(any(IdempotencyKeyEntity.class))).thenAnswer(i -> {
            IdempotencyKeyEntity record = i.getArgument(0);
            records.put("release-key:/release", record);
            return record;
        });
        IdempotencyService service = new IdempotencyService(repository, mapper);
        AtomicInteger executions = new AtomicInteger();
        UUID dealId = UUID.randomUUID();
        var action = (java.util.function.Supplier<ResponseEntity<String>>) () -> ResponseEntity.ok("call-" + executions.incrementAndGet());
        assertEquals("call-1", service.execute("release-key", "/release", dealId, Map.of("cents", 98000), String.class, action).getBody());
        assertEquals("call-1", service.execute("release-key", "/release", dealId, Map.of("cents", 98000), String.class, action).getBody());
        assertThrows(IdempotencyConflictException.class,
                () -> service.execute("release-key", "/release", dealId, Map.of("cents", 98001), String.class, action));
        assertEquals(1, executions.get());
    }

    @Test
    void tst11_existingDisputeBlocksCaptureBeforeAnyPayPalCall() {
        PayPalOrderService orders = mock(PayPalOrderService.class);
        PayPalInvoiceService invoices = mock(PayPalInvoiceService.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
        DealPort deals = mock(DealPort.class);
        AuditEventPort audit = mock(AuditEventPort.class);
        UUID dealId = UUID.randomUUID();
        when(deals.isDealDisputed(dealId)).thenReturn(true);
        SettlementService service = new SettlementService(invoices, orders, invoiceRepository, payments, deals, audit);
        assertThrows(SettlementConflictException.class,
                () -> service.acceptDeliverables(dealId, 1, "disputed-capture", "buyer@example.com"));
        verify(deals).assertBuyer(dealId, "buyer@example.com");
        verifyNoInteractions(orders, invoices, payments, invoiceRepository, audit);
    }
    @Test
    void tst15_manualReconciliationKeepsUnknownUntilPayPalReturnsKnownState() {
        UUID dealId = UUID.randomUUID();
        PaymentRepository payments = mock(PaymentRepository.class);
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        PayPalOrderService orders = mock(PayPalOrderService.class);
        PaymentEntity payment = new PaymentEntity(UUID.randomUUID(), dealId, 1, PaymentLifecycleStatus.UNKNOWN,
                "ORDER-UNKNOWN", null, null, null, 98000L, "USD");
        when(payments.findFirstByDealIdOrderByUpdatedAtDesc(dealId)).thenReturn(Optional.of(payment));
        when(orders.getOrder("ORDER-UNKNOWN")).thenReturn(
                new com.payagent.paypal.model.PayPalOrderResponse("ORDER-UNKNOWN", "UNRECOGNIZED", "AUTHORIZE", null, null),
                new com.payagent.paypal.model.PayPalOrderResponse("ORDER-UNKNOWN", "COMPLETED", "AUTHORIZE", null, null));
        when(orders.mapOrderStatus("UNRECOGNIZED")).thenReturn(PaymentLifecycleStatus.UNKNOWN);
        when(orders.mapOrderStatus("COMPLETED")).thenReturn(PaymentLifecycleStatus.COMPLETED);
        SettlementReconciliationService service = new SettlementReconciliationService(mock(PayPalEventRepository.class),
                invoices, payments, mock(PayPalInvoiceService.class), orders, mock(AuditEventPort.class), mock(com.payagent.settlement.port.DealPort.class));
        service.reconcileDeal(dealId);
        assertEquals(PaymentLifecycleStatus.UNKNOWN, payment.getLifecycleStatus());
        verify(payments, never()).save(any());
        service.reconcileDeal(dealId);
        assertEquals(PaymentLifecycleStatus.COMPLETED, payment.getLifecycleStatus());
        verify(payments).save(payment);
        verify(orders, times(2)).getOrder("ORDER-UNKNOWN");
    }

}
