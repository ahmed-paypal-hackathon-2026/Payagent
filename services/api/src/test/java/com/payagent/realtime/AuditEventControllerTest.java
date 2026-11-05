package com.payagent.realtime;

import com.payagent.auth.User;
import com.payagent.auth.UserRepository;
import com.payagent.realtime.model.AuditEventDTO;
import com.payagent.realtime.service.AuditEventService;
import com.payagent.settlement.exception.SettlementForbiddenException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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

    @MockBean
    private UserRepository userRepository;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        user = new User(userId, "buyer@test.local", "hash", null);
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void getDealTimeline_validUuidPrincipal_returnsEvents() throws Exception {
        UUID dealId = UUID.randomUUID();
        AuditEventDTO event = new AuditEventDTO(
                1L,
                dealId,
                1L,
                "DEAL_CREATED",
                Map.of("title", "Landing Page"),
                Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(auditEventService.getTimeline(eq(dealId), eq(0L), eq("buyer@test.local")))
                .thenReturn(List.of(event));

        mockMvc.perform(get("/api/v1/deals/{id}/events", dealId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("DEAL_CREATED"))
                .andExpect(jsonPath("$[0].seq").value(1));
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void getDealTimeline_uuidNotFound_returnsBadRequest() throws Exception {
        UUID dealId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/deals/{id}/events", dealId))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "not-a-uuid")
    void getDealTimeline_malformedPrincipal_returnsBadRequest() throws Exception {
        UUID dealId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/deals/{id}/events", dealId))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "22222222-2222-2222-2222-222222222222")
    void getDealTimeline_unauthorizedUser_returnsForbidden() throws Exception {
        UUID dealId = UUID.randomUUID();
        UUID intruderId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        User intruder = new User(intruderId, "intruder@test.local", "hash", null);

        when(userRepository.findById(intruderId)).thenReturn(Optional.of(intruder));
        when(auditEventService.getTimeline(eq(dealId), eq(0L), eq("intruder@test.local")))
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
