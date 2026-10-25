package com.payagent.deals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class DealIntakeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void intakeSuccess() throws Exception {
        UUID user = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(new DealController.IntakeRequest("Build a website in USD 1000", "USD"));
        mockMvc.perform(post("/api/v1/deals/intake")
                .principal(() -> user.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealId").exists())
                .andExpect(jsonPath("$.terms").exists())
                .andExpect(jsonPath("$.inviteToken").exists());
    }

    @Test
    void intakeInvalidInput() throws Exception {
        UUID user = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(new DealController.IntakeRequest("", "USD"));
        mockMvc.perform(post("/api/v1/deals/intake")
                .principal(() -> user.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isUnprocessableEntity());
    }
}
