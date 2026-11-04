package com.payagent.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.realtime.model.AuditEventDTO;
import com.payagent.realtime.service.AuditEventService;
import com.payagent.realtime.service.DealWebSocketBroker;
import com.payagent.settlement.exception.SettlementForbiddenException;
import com.payagent.settlement.persistence.AuditEventRepository;
import com.payagent.settlement.port.DealPort;
import java.util.List;
import java.util.Map;
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
class AuditEventServiceTest {

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private DealPort dealPort;

    @MockBean
    private DealWebSocketBroker webSocketBroker;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuditEventService auditEventService;

    @BeforeEach
    void setUp() {
        auditEventService = new AuditEventService(
                auditEventRepository,
                dealPort,
                jdbcTemplate,
                objectMapper,
                webSocketBroker
        );
    }

    @Test
    void recordEvent_assignsMonotonicSequencesAndBroadcasts() {
        UUID dealId = UUID.randomUUID();

        auditEventService.recordEvent(dealId, "DEAL_CREATED", Map.of("title", "Logo Design"));
        auditEventService.recordEvent(dealId, "TERMS_UPDATED", Map.of("price", 15000));
        auditEventService.recordEvent(dealId, "OFFER_SUBMITTED", Map.of("round", 1));

        verify(webSocketBroker, times(3)).broadcastDealEvent(eq(dealId), any());

        long maxSeq = auditEventRepository.findMaxSeqByDealId(dealId);
        assertThat(maxSeq).isEqualTo(3);
    }

    @Test
    void getTimeline_returnsEventsOrderedBySeq_andFiltersByAfterSeq() {
        UUID dealId = UUID.randomUUID();
        String buyerEmail = "buyer@test.local";
        when(dealPort.getBuyerEmail(dealId)).thenReturn(buyerEmail);

        auditEventService.recordEvent(dealId, "EVENT_1", Map.of("step", 1));
        auditEventService.recordEvent(dealId, "EVENT_2", Map.of("step", 2));
        auditEventService.recordEvent(dealId, "EVENT_3", Map.of("step", 3));

        List<AuditEventDTO> allEvents = auditEventService.getTimeline(dealId, 0, buyerEmail);
        assertThat(allEvents).hasSize(3);
        assertThat(allEvents.get(0).seq()).isEqualTo(1);
        assertThat(allEvents.get(1).seq()).isEqualTo(2);
        assertThat(allEvents.get(2).seq()).isEqualTo(3);

        List<AuditEventDTO> eventsAfter1 = auditEventService.getTimeline(dealId, 1, buyerEmail);
        assertThat(eventsAfter1).hasSize(2);
        assertThat(eventsAfter1.get(0).seq()).isEqualTo(2);
        assertThat(eventsAfter1.get(1).seq()).isEqualTo(3);
    }

    @Test
    void getTimeline_rejectsNonParticipant() {
        UUID dealId = UUID.randomUUID();
        String unauthorizedEmail = "intruder@test.local";
        doThrow(new SettlementForbiddenException("Caller is not a participant in deal " + dealId))
                .when(dealPort).assertParty(dealId, unauthorizedEmail);

        assertThatThrownBy(() -> auditEventService.getTimeline(dealId, 0, unauthorizedEmail))
                .isInstanceOf(SettlementForbiddenException.class);
    }

    @Test
    void getTimeline_masksCounterpartyPrivateMandateLimits() {
        UUID dealId = UUID.randomUUID();
        String buyerEmail = "buyer@test.local";
        String sellerEmail = "seller@test.local";

        when(dealPort.getBuyerEmail(dealId)).thenReturn(buyerEmail);
        when(dealPort.getSellerEmail(dealId)).thenReturn(sellerEmail);

        // Record a seller mandate event with private limit prices
        Map<String, Object> sellerMandatePayload = Map.of(
                "partyRole", "SELLER",
                "limitPriceMinorUnits", 80000L,
                "targetPriceMinorUnits", 95000L,
                "forbiddenTerms", List.of("unlimited revisions")
        );
        auditEventService.recordEvent(dealId, "SELLER_MANDATE_LOCKED", sellerMandatePayload);

        // Buyer inspects timeline: counterparty private limits must be masked!
        List<AuditEventDTO> buyerTimeline = auditEventService.getTimeline(dealId, 0, buyerEmail);
        assertThat(buyerTimeline).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> buyerViewPayload = (Map<String, Object>) buyerTimeline.get(0).payload();
        assertThat(buyerViewPayload).doesNotContainKey("limitPriceMinorUnits");
        assertThat(buyerViewPayload).doesNotContainKey("targetPriceMinorUnits");
        assertThat(buyerViewPayload).doesNotContainKey("forbiddenTerms");
        assertThat(buyerViewPayload.get("privateLimitsMasked")).isEqualTo(true);

        // Seller inspects own timeline: can see own mandate limits
        List<AuditEventDTO> sellerTimeline = auditEventService.getTimeline(dealId, 0, sellerEmail);
        assertThat(sellerTimeline).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> sellerViewPayload = (Map<String, Object>) sellerTimeline.get(0).payload();
        assertThat(sellerViewPayload).containsKey("limitPriceMinorUnits");
    }
}
