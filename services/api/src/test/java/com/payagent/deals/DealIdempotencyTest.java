package com.payagent.deals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class DealIdempotencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DealRepository dealRepository;

    @Test
    void createDealIdempotentRepeat() throws Exception {
        UUID user = UUID.randomUUID();
        String key = "deal-create-idemp";

        String resp1 = mockMvc.perform(post("/api/v1/deals")
                .header("Idempotency-Key", key)
                .principal(() -> user.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealId").exists())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/v1/deals")
                .header("Idempotency-Key", key)
                .principal(() -> user.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(equalTo(resp1)));
    }

    @Test
    void createDealConflictOnDifferentPayloadFails() throws Exception {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        String key = "deal-create-conflict";

        mockMvc.perform(post("/api/v1/deals")
                .header("Idempotency-Key", key)
                .principal(() -> user1.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/deals")
                .header("Idempotency-Key", key)
                .principal(() -> user2.toString()))
                .andExpect(status().isConflict());
    }

    @Test
    void joinDealIdempotentRepeat() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, null, com.payagent.common.contracts.DealStatus.DRAFT));

        String key = "deal-join-idemp";
        String url = "/api/v1/deals/" + dealId + "/join";

        mockMvc.perform(post(url)
                .header("Idempotency-Key", key)
                .principal(() -> seller.toString())
                .contentType("application/json")
                .content("{\"inviteToken\":\"x\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(url)
                .header("Idempotency-Key", key)
                .principal(() -> seller.toString())
                .contentType("application/json")
                .content("{\"inviteToken\":\"x\"}"))
                .andExpect(status().isOk());
    }
}
