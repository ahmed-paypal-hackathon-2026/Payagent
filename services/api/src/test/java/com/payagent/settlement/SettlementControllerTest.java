package com.payagent.settlement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.common.contracts.InvoiceLifecycleStatus;
import com.payagent.common.contracts.PaymentLifecycleStatus;
import com.payagent.common.contracts.SettlementStateDTO;
import com.payagent.settlement.exception.SettlementConflictException;
import com.payagent.settlement.exception.SettlementForbiddenException;
import com.payagent.settlement.exception.SettlementNotFoundException;
import com.payagent.settlement.persistence.IdempotencyKeyRepository;
import java.security.Principal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SettlementControllerTest {

    private SettlementService settlementService;
    private IdempotencyKeyRepository idempotencyRepository;
    private MockMvc mockMvc;
    private MockMvc acceptanceMockMvc;
    private final UUID dealId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        settlementService = mock(SettlementService.class);
        idempotencyRepository = mock(IdempotencyKeyRepository.class);
        when(idempotencyRepository.findByIdempotencyKeyAndRoute(anyString(), anyString())).thenReturn(Optional.empty());

        ObjectMapper objectMapper = new ObjectMapper();
        IdempotencyService idempotencyService = new IdempotencyService(idempotencyRepository, objectMapper);

        SettlementController settlementController = new SettlementController(settlementService, idempotencyService);
        DeliverablesAcceptanceController acceptanceController = new DeliverablesAcceptanceController(settlementService, idempotencyService);

        mockMvc = MockMvcBuilders.standaloneSetup(settlementController)
                .setControllerAdvice(new SettlementExceptionHandler())
                .build();

        acceptanceMockMvc = MockMvcBuilders.standaloneSetup(acceptanceController)
                .setControllerAdvice(new SettlementExceptionHandler())
                .build();
    }

    @Test
    void initiateSettlementReturns200AndState() throws Exception {
        SettlementStateDTO state = new SettlementStateDTO(
                dealId.toString(),
                1,
                InvoiceLifecycleStatus.SENT,
                "INV2-111",
                "INV-01",
                "https://paypal.sandbox/view",
                PaymentLifecycleStatus.ORDER_CREATED,
                "ORD-222",
                null,
                null,
                null,
                98000L,
                "USD",
                "2026-10-25T12:00:00Z"
        );
        when(settlementService.initiateSettlement(eq(dealId), anyString(), any())).thenReturn(state);

        mockMvc.perform(post("/api/v1/deals/{id}/settlement/initiate", dealId)
                        .header("Idempotency-Key", "test-key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealId").value(dealId.toString()))
                .andExpect(jsonPath("$.invoiceStatus").value("SENT"))
                .andExpect(jsonPath("$.paymentStatus").value("ORDER_CREATED"))
                .andExpect(jsonPath("$.amountMinorUnits").value(98000));
    }

    @Test
    void initiateSettlementFailsWithoutIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/deals/{id}/settlement/initiate", dealId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authorizeSettlementReturnsAuthorizedState() throws Exception {
        SettlementStateDTO state = new SettlementStateDTO(
                dealId.toString(),
                1,
                InvoiceLifecycleStatus.SENT,
                "INV2-111",
                "INV-01",
                "https://paypal.sandbox/view",
                PaymentLifecycleStatus.AUTHORIZED,
                "ORD-222",
                "AUTH-333",
                null,
                "2026-11-23T12:00:00Z",
                98000L,
                "USD",
                "2026-10-25T12:00:00Z"
        );
        when(settlementService.authorizeSettlement(eq(dealId), eq("ORD-222"), anyString(), any())).thenReturn(state);

        mockMvc.perform(post("/api/v1/deals/{id}/settlement/authorize", dealId)
                        .header("Idempotency-Key", "test-key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paypalOrderId\":\"ORD-222\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("AUTHORIZED"))
                .andExpect(jsonPath("$.paypalAuthorizationId").value("AUTH-333"));
    }

    @Test
    void acceptDeliverablesReturns409WhenDisputed() throws Exception {
        when(settlementService.acceptDeliverables(eq(dealId), anyInt(), anyString(), any()))
                .thenThrow(new SettlementConflictException("Capture blocked: deal is in DISPUTED status"));

        acceptanceMockMvc.perform(post("/api/v1/deals/{id}/accept-deliverables", dealId)
                        .header("Idempotency-Key", "test-key-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evidenceVersion\": 1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Capture blocked: deal is in DISPUTED status"));
    }

    @Test
    void acceptDeliverablesReturns403WhenCallerNotBuyer() throws Exception {
        when(settlementService.acceptDeliverables(eq(dealId), anyInt(), anyString(), any()))
                .thenThrow(new SettlementForbiddenException("Caller is not authorized BUYER"));

        acceptanceMockMvc.perform(post("/api/v1/deals/{id}/accept-deliverables", dealId)
                        .header("Idempotency-Key", "test-key-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evidenceVersion\": 1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void getSettlementReturns404WhenNotFound() throws Exception {
        when(settlementService.getSettlementState(dealId))
                .thenThrow(new SettlementNotFoundException("No settlement state exists"));

        mockMvc.perform(get("/api/v1/deals/{id}/settlement", dealId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
