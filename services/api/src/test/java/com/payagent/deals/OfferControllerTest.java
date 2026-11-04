package com.payagent.deals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.mandates.Mandate;
import com.payagent.mandates.MandateRepository;
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
class OfferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private MandateRepository mandateRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createOfferValid() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(sampleMandate(dealId, PartyRole.BUYER, buyer));

        StructuredTermsDTO terms = new StructuredTermsDTO("t", List.of("core"), 90000L, "USD", 15, 4, "ok");
        OfferController.OfferRequest req = new OfferController.OfferRequest(terms, "initial offer");
        String key = "offer-key-1";
        String url = "/api/v1/deals/" + dealId + "/offers";

        mockMvc.perform(post(url)
                .header("Idempotency-Key", key)
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealId").value(dealId.toString()))
                .andExpect(jsonPath("$.mandateCompliant").value(true));
    }

    @Test
    void createOfferMandateVetoPrice() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(sampleMandate(dealId, PartyRole.BUYER, buyer));

        StructuredTermsDTO terms = new StructuredTermsDTO("t", List.of("core"), 200000L, "USD", 15, 4, "ok");
        OfferController.OfferRequest req = new OfferController.OfferRequest(terms, "too high");
        String key = "offer-key-veto";
        String url = "/api/v1/deals/" + dealId + "/offers";

        mockMvc.perform(post(url)
                .header("Idempotency-Key", key)
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createOfferNonParticipantDenied() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));

        StructuredTermsDTO terms = new StructuredTermsDTO("t", List.of("core"), 90000L, "USD", 15, 4, "ok");
        OfferController.OfferRequest req = new OfferController.OfferRequest(terms, "msg");
        String url = "/api/v1/deals/" + dealId + "/offers";

        mockMvc.perform(post(url)
                .header("Idempotency-Key", "k")
                .principal(() -> outsider.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createOfferIdempotentRepeat() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(sampleMandate(dealId, PartyRole.BUYER, buyer));

        StructuredTermsDTO terms = new StructuredTermsDTO("t", List.of("core"), 90000L, "USD", 15, 4, "ok");
        OfferController.OfferRequest req = new OfferController.OfferRequest(terms, "msg");
        String key = "offer-idemp";
        String url = "/api/v1/deals/" + dealId + "/offers";

        String resp1 = mockMvc.perform(post(url)
                .header("Idempotency-Key", key)
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post(url)
                .header("Idempotency-Key", key)
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().string(equalTo(resp1)));
    }

    @Test
    void createOfferConflictPayload() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(sampleMandate(dealId, PartyRole.BUYER, buyer));

        StructuredTermsDTO terms1 = new StructuredTermsDTO("t", List.of("core"), 90000L, "USD", 15, 4, "ok");
        StructuredTermsDTO terms2 = new StructuredTermsDTO("t", List.of("core"), 95000L, "USD", 15, 4, "ok");
        String key = "offer-conflict";
        String url = "/api/v1/deals/" + dealId + "/offers";

        mockMvc.perform(post(url)
                .header("Idempotency-Key", key)
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OfferController.OfferRequest(terms1, "msg"))))
                .andExpect(status().isOk());

        mockMvc.perform(post(url)
                .header("Idempotency-Key", key)
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OfferController.OfferRequest(terms2, "msg"))))
                .andExpect(status().isConflict());
    }

    private Mandate sampleMandate(UUID dealId, PartyRole role, UUID dummy) {
        return new Mandate(UUID.randomUUID(), dealId, role, 1, "USD", 120000L, 90000L, 20, 15, 2, 4, "[]", "[]", NegotiationMode.ASSISTED, 10, OffsetDateTime.now().plusYears(1));
    }
}
