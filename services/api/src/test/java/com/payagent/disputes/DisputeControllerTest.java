package com.payagent.disputes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.common.contracts.DisputeStatus;
import com.payagent.disputes.model.CreateDisputeRequest;
import com.payagent.disputes.model.DisputeDTO;
import com.payagent.disputes.model.ResolveDisputeRequest;
import com.payagent.disputes.service.DisputeService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DisputeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DisputeService disputeService;

    @Test
    @WithMockUser(username = "buyer@test.local")
    void openDispute_validRequest_returnsDisputeDTO() throws Exception {
        UUID dealId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        CreateDisputeRequest request = new CreateDisputeRequest("Deliverable incomplete", "Fix missing pages");
        DisputeDTO responseDTO = new DisputeDTO(
                disputeId,
                dealId,
                DisputeStatus.OPEN,
                "Deliverable incomplete",
                "Fix missing pages",
                Instant.now(),
                null
        );

        when(disputeService.createDispute(eq(dealId), any(), eq("buyer@test.local")))
                .thenReturn(responseDTO);

        mockMvc.perform(post("/api/v1/deals/{id}/disputes", dealId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(disputeId.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reason").value("Deliverable incomplete"));
    }

    @Test
    @WithMockUser(username = "buyer@test.local")
    void listDisputes_returnsList() throws Exception {
        UUID dealId = UUID.randomUUID();
        DisputeDTO dto = new DisputeDTO(
                UUID.randomUUID(),
                dealId,
                DisputeStatus.OPEN,
                "Reason",
                "Req",
                Instant.now(),
                null
        );

        when(disputeService.getDisputesForDeal(eq(dealId), eq("buyer@test.local")))
                .thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/deals/{id}/disputes", dealId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    @WithMockUser(username = "buyer@test.local")
    void resolveDispute_validRequest_returnsResolvedDTO() throws Exception {
        UUID dealId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        ResolveDisputeRequest request = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_RELEASE_CAPTURE,
                "Parties reached settlement"
        );
        DisputeDTO responseDTO = new DisputeDTO(
                disputeId,
                dealId,
                DisputeStatus.RESOLVED_RELEASE_CAPTURE,
                "Reason",
                "Req",
                Instant.now(),
                Instant.now()
        );

        when(disputeService.resolveDispute(eq(dealId), eq(disputeId), any(), eq("buyer@test.local")))
                .thenReturn(responseDTO);

        mockMvc.perform(post("/api/v1/deals/{id}/disputes/{disputeId}/resolve", dealId, disputeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED_RELEASE_CAPTURE"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
    }
}
