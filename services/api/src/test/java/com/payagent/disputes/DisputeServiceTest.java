package com.payagent.disputes;

import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.DisputeStatus;
import com.payagent.disputes.model.CreateDisputeRequest;
import com.payagent.disputes.model.DisputeDTO;
import com.payagent.disputes.model.ResolveDisputeRequest;
import com.payagent.disputes.persistence.DisputeRepository;
import com.payagent.disputes.service.DisputeService;
import com.payagent.settlement.SettlementService;
import com.payagent.settlement.exception.SettlementConflictException;
import com.payagent.settlement.port.DealPort;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DisputeServiceTest {

    @Autowired
    private DisputeRepository disputeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private DealPort dealPort;

    @MockBean
    private com.payagent.realtime.service.AuditEventService auditEventService;

    @MockBean
    private SettlementService settlementService;

    @Autowired
    private com.payagent.auth.UserRepository userRepository;

    private DisputeService disputeService;
    private UUID testBuyerId;

    @BeforeEach
    void setUp() {
        com.payagent.auth.User user = userRepository.save(new com.payagent.auth.User(
                UUID.randomUUID(),
                "buyer-" + UUID.randomUUID() + "@test.local",
                "hash",
                java.time.OffsetDateTime.now()
        ));
        testBuyerId = user.getId();

        disputeService = new DisputeService(
                disputeRepository,
                dealPort,
                auditEventService,
                jdbcTemplate
        );
    }

    @Test
    void createDispute_transitionsDealToDisputed_andPausesAutomation() {
        UUID dealId = UUID.randomUUID();
        String buyerEmail = "buyer@test.local";
        when(dealPort.getDealStatus(dealId)).thenReturn(DealStatus.DELIVERABLES_SUBMITTED);

        // Insert deal in deals table so jdbcTemplate update succeeds
        jdbcTemplate.update("INSERT INTO deals (id, buyer_user_id, status, stop_reason, created_at, updated_at) VALUES (?, ?, 'DELIVERABLES_SUBMITTED', 'NONE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", dealId, testBuyerId);

        CreateDisputeRequest request = new CreateDisputeRequest(
                "Deliverable does not meet requirements",
                "Requesting revision or partial refund"
        );

        DisputeDTO dto = disputeService.createDispute(dealId, request, buyerEmail);

        assertThat(dto).isNotNull();
        assertThat(dto.status()).isEqualTo(DisputeStatus.OPEN);
        assertThat(dto.reason()).isEqualTo("Deliverable does not meet requirements");

        // Verify DealStatus transitioned to DISPUTED
        verify(dealPort).transitionDealStatus(dealId, DealStatus.DISPUTED);

        // Verify stop_reason updated to USER_PAUSED
        String stopReason = jdbcTemplate.queryForObject("SELECT stop_reason FROM deals WHERE id = ?", String.class, dealId);
        assertThat(stopReason).isEqualTo("USER_PAUSED");

        // Verify Audit event recorded
        verify(auditEventService).recordEvent(eq(dealId), eq("DISPUTE_OPENED"), any());
    }

    @Test
    void createDispute_onClosedDeal_throwsConflictException() {
        UUID dealId = UUID.randomUUID();
        String buyerEmail = "buyer@test.local";
        when(dealPort.getDealStatus(dealId)).thenReturn(DealStatus.CLOSED);

        CreateDisputeRequest request = new CreateDisputeRequest("Too late", "Refund");

        assertThatThrownBy(() -> disputeService.createDispute(dealId, request, buyerEmail))
                .isInstanceOf(SettlementConflictException.class)
                .hasMessageContaining("Cannot open dispute on deal");
    }

    @Test
    void resolveDispute_releaseCapture_transitionsDealToDeliverablesSubmitted() {
        UUID dealId = UUID.randomUUID();
        String callerEmail = "buyer@test.local";
        when(dealPort.getDealStatus(dealId)).thenReturn(DealStatus.IN_PROGRESS);

        jdbcTemplate.update("INSERT INTO deals (id, buyer_user_id, status, stop_reason, created_at, updated_at) VALUES (?, ?, 'IN_PROGRESS', 'NONE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", dealId, testBuyerId);

        DisputeDTO opened = disputeService.createDispute(
                dealId,
                new CreateDisputeRequest("Misunderstanding", "Clarified"),
                callerEmail
        );

        ResolveDisputeRequest resolveRequest = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_RELEASE_CAPTURE,
                "Both parties agreed to release funds"
        );

        DisputeDTO resolved = disputeService.resolveDispute(dealId, opened.id(), resolveRequest, callerEmail);

        assertThat(resolved.status()).isEqualTo(DisputeStatus.RESOLVED_RELEASE_CAPTURE);
        assertThat(resolved.resolvedAt()).isNotNull();

        // Verify deal unfreezes back to DELIVERABLES_SUBMITTED
        verify(dealPort).transitionDealStatus(dealId, DealStatus.DELIVERABLES_SUBMITTED);
        verify(auditEventService).recordEvent(eq(dealId), eq("DISPUTE_RESOLVED"), any());
    }

    @Test
    void resolveDispute_voidAuthorization_transitionsDealToClosed() {
        UUID dealId = UUID.randomUUID();
        String callerEmail = "buyer@test.local";
        when(dealPort.getDealStatus(dealId)).thenReturn(DealStatus.IN_PROGRESS);

        jdbcTemplate.update("INSERT INTO deals (id, buyer_user_id, status, stop_reason, created_at, updated_at) VALUES (?, ?, 'IN_PROGRESS', 'NONE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", dealId, testBuyerId);

        DisputeDTO opened = disputeService.createDispute(
                dealId,
                new CreateDisputeRequest("Cancellation", "Full refund"),
                callerEmail
        );

        ResolveDisputeRequest resolveRequest = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_VOID_AUTHORIZATION,
                "Mutual agreement to void hold and close deal"
        );

        DisputeDTO resolved = disputeService.resolveDispute(dealId, opened.id(), resolveRequest, callerEmail);

        assertThat(resolved.status()).isEqualTo(DisputeStatus.RESOLVED_VOID_AUTHORIZATION);
        verify(dealPort).transitionDealStatus(dealId, DealStatus.CLOSED);
    }
}
