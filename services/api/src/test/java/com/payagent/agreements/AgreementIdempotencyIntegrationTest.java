package com.payagent.agreements;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.deals.Deal;
import com.payagent.deals.DealRepository;
import com.payagent.mandates.Mandate;
import com.payagent.mandates.MandateRepository;
import com.payagent.mandates.UtilityCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class AgreementIdempotencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private MandateRepository mandateRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void proposeAgreementIdempotentRepeat() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(sampleMandate(dealId, PartyRole.BUYER, buyer));
        mandateRepository.save(sampleMandate(dealId, PartyRole.SELLER, seller));

        StructuredTermsDTO terms = new StructuredTermsDTO("t", List.of("core"), 90000L, "USD", 15, 4, "ok");
        String key = "idemp-key-1";

        String json = objectMapper.writeValueAsString(terms);

        String url = "/api/v1/deals/" + dealId + "/agreements/propose";

        // First call
        String resp1 = mockMvc.perform(post(url)
                .header("Idempotency-Key", key)
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();

        // Second identical call
        mockMvc.perform(post(url)
                .header("Idempotency-Key", key)
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(content().string(equalTo(resp1)));
    }

    @Test
    void proposeAgreementConflictOnDifferentPayload() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(sampleMandate(dealId, PartyRole.BUYER, buyer));
        mandateRepository.save(sampleMandate(dealId, PartyRole.SELLER, seller));

        StructuredTermsDTO terms1 = new StructuredTermsDTO("t", List.of("core"), 90000L, "USD", 15, 4, "ok");
        StructuredTermsDTO terms2 = new StructuredTermsDTO("t", List.of("core"), 95000L, "USD", 15, 4, "ok");
        String key = "idemp-key-conflict";

        String url = "/api/v1/deals/" + dealId + "/agreements/propose";

        mockMvc.perform(post(url)
                .header("Idempotency-Key", key)
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(terms1)))
                .andExpect(status().isOk());

        mockMvc.perform(post(url)
                .header("Idempotency-Key", key)
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(terms2)))
                .andExpect(status().isConflict());
    }

    private Mandate sampleMandate(UUID dealId, PartyRole role, UUID dummy) {
        return new Mandate(UUID.randomUUID(), dealId, role, 1, "USD", 120000L, 90000L, 20, 15, 2, 4, "[]", "[]", com.payagent.common.contracts.NegotiationMode.ASSISTED, 10, OffsetDateTime.now().plusYears(1));
    }
}
