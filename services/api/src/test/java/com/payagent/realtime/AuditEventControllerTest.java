package com.payagent.realtime;

import com.payagent.realtime.model.AuditEventDTO;
import com.payagent.realtime.service.AuditEventService;
import com.payagent.settlement.exception.SettlementForbiddenException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditEventService auditEventService;

    @Test
    @WithMockUser(username = "buyer@test.local")
    void getDealTimeline_authenticatedUser_returnsEvents() throws Exception {
        UUID dealId = UUID.randomUUID();
        AuditEventDTO event = new AuditEventDTO(
                1L,
                dealId,
                1L,
                "DEAL_CREATED",
                Map.of("title", "Landing Page"),
                Instant.now()
        );

        when(auditEventService.getTimeline(eq(dealId), eq(0L), any()))
                .thenReturn(List.of(event));

        mockMvc.perform(get("/api/v1/deals/{id}/events", dealId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("DEAL_CREATED"))
                .andExpect(jsonPath("$[0].seq").value(1));
    }

    @Test
    @WithMockUser(username = "intruder@test.local")
    void getDealTimeline_unauthorizedUser_returnsForbidden() throws Exception {
        UUID dealId = UUID.randomUUID();
        when(auditEventService.getTimeline(eq(dealId), eq(0L), any()))
                .thenThrow(new SettlementForbiddenException("Not authorized"));

        mockMvc.perform(get("/api/v1/deals/{id}/events", dealId))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDealTimeline_unauthenticated_returnsUnauthorized() throws Exception {
        UUID dealId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/deals/{id}/events", dealId))
                .andExpect(status().isUnauthorized());
    }
}
