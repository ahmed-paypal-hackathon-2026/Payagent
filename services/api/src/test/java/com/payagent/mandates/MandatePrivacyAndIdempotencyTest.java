package com.payagent.mandates;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import com.payagent.deals.Deal;
import com.payagent.deals.DealRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class MandatePrivacyAndIdempotencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private MandateRepository mandateRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getMandateAuthorizedParticipantReturnsOwnMandate() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(sampleMandate(dealId, PartyRole.BUYER, buyer));
        mandateRepository.save(sampleMandate(dealId, PartyRole.SELLER, seller));

        mockMvc.perform(get("/api/v1/deals/" + dealId + "/mandates")
                .principal(() -> buyer.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partyRole").value("BUYER"))
                .andExpect(jsonPath("$.mustHaveDeliverables").isEmpty())
                .andExpect(jsonPath("$.forbiddenTerms").isEmpty());
    }

    @Test
    void getMandateNonParticipantDenied() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(sampleMandate(dealId, PartyRole.BUYER, buyer));

        mockMvc.perform(get("/api/v1/deals/" + dealId + "/mandates")
                .principal(() -> outsider.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void createMandateIdempotentRepeat() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));

        MandateService.MandateCreateRequest req = new MandateService.MandateCreateRequest(
                "USD", 120000L, 90000L, 20, 15, 2, 4, java.util.List.of("core"), java.util.List.of(), NegotiationMode.ASSISTED, 10, OffsetDateTime.now().plusYears(1)
        );
        String key = "mandate-idemp-1";
        String url = "/api/v1/deals/" + dealId + "/mandates";

        String json = objectMapper.writeValueAsString(req);

        String resp1 = mockMvc.perform(post(url)
                .header("Idempotency-Key", key)
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post(url)
                .header("Idempotency-Key", key)
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(content().string(equalTo(resp1)));
    }

    @Test
    void createMandateConflictOnDifferentPayload() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));

        MandateService.MandateCreateRequest req1 = new MandateService.MandateCreateRequest(
                "USD", 120000L, 90000L, 20, 15, 2, 4, java.util.List.of("core"), java.util.List.of(), NegotiationMode.ASSISTED, 10, OffsetDateTime.now().plusYears(1)
        );
        MandateService.MandateCreateRequest req2 = new MandateService.MandateCreateRequest(
                "USD", 130000L, 95000L, 20, 15, 2, 4, java.util.List.of("core"), java.util.List.of(), NegotiationMode.ASSISTED, 10, OffsetDateTime.now().plusYears(1)
        );
        String key = "mandate-idemp-conflict";
        String url = "/api/v1/deals/" + dealId + "/mandates";

        mockMvc.perform(post(url)
                .header("Idempotency-Key", key)
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk());

        mockMvc.perform(post(url)
                .header("Idempotency-Key", key)
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict());
    }

    private Mandate sampleMandate(UUID dealId, PartyRole role, UUID dummy) {
        return new Mandate(UUID.randomUUID(), dealId, role, 1, "USD", 120000L, 90000L, 20, 15, 2, 4, "[]", "[]", NegotiationMode.ASSISTED, 10, OffsetDateTime.now().plusYears(1));
    }
}
