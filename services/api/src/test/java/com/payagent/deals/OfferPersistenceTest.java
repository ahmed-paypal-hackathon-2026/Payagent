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
class OfferPersistenceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private MandateRepository mandateRepository;

    @Autowired
    private OfferRepository offerRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void offersPersistWithIncrementingRoundsPerDeal() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(sampleMandate(dealId, PartyRole.BUYER, buyer));

        StructuredTermsDTO terms1 = new StructuredTermsDTO("t", List.of("core"), 90000L, "USD", 15, 4, "ok");
        StructuredTermsDTO terms2 = new StructuredTermsDTO("t", List.of("core"), 92000L, "USD", 15, 4, "ok");

        String url = "/api/v1/deals/" + dealId + "/offers";

        mockMvc.perform(post(url)
                .header("Idempotency-Key", "k1")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OfferController.OfferRequest(terms1, "first"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundNumber").value(1));

        mockMvc.perform(post(url)
                .header("Idempotency-Key", "k2")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OfferController.OfferRequest(terms2, "second"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundNumber").value(2));

        List<Offer> offers = offerRepository.findByDealIdOrderByRoundNumberDesc(dealId);
        org.junit.jupiter.api.Assertions.assertEquals(2, offers.size());
        org.junit.jupiter.api.Assertions.assertEquals(2, offers.get(0).getRoundNumber());
        org.junit.jupiter.api.Assertions.assertEquals(1, offers.get(1).getRoundNumber());

        Offer o1 = offers.get(1);
        org.junit.jupiter.api.Assertions.assertEquals(PartyRole.BUYER, o1.getProposerRole());
        org.junit.jupiter.api.Assertions.assertFalse(o1.getGeneratedByAgent());
        org.junit.jupiter.api.Assertions.assertEquals("first", o1.getRationaleSummary());
        org.junit.jupiter.api.Assertions.assertTrue(o1.getMandateCompliant());
        org.junit.jupiter.api.Assertions.assertNotNull(o1.getCallerUtilityScore());
        org.junit.jupiter.api.Assertions.assertNotNull(o1.getTermsJson());
        org.junit.jupiter.api.Assertions.assertTrue(o1.getTermsJson().contains("\"priceMinorUnits\":90000"));
        org.junit.jupiter.api.Assertions.assertNotNull(o1.getCreatedAt());
    }

    @Test
    void differentDealsHaveIndependentRoundNumbers() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId1 = UUID.randomUUID();
        UUID dealId2 = UUID.randomUUID();
        dealRepository.save(new Deal(dealId1, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        dealRepository.save(new Deal(dealId2, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(sampleMandate(dealId1, PartyRole.BUYER, buyer));
        mandateRepository.save(sampleMandate(dealId2, PartyRole.BUYER, buyer));

        StructuredTermsDTO terms = new StructuredTermsDTO("t", List.of("core"), 90000L, "USD", 15, 4, "ok");

        mockMvc.perform(post("/api/v1/deals/" + dealId1 + "/offers")
                .header("Idempotency-Key", "d1-k1")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OfferController.OfferRequest(terms, "msg"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundNumber").value(1));

        mockMvc.perform(post("/api/v1/deals/" + dealId2 + "/offers")
                .header("Idempotency-Key", "d2-k1")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OfferController.OfferRequest(terms, "msg"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundNumber").value(1));
    }

    @Test
    void idempotentReplayDoesNotCreateDuplicateOffer() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(sampleMandate(dealId, PartyRole.BUYER, buyer));

        StructuredTermsDTO terms = new StructuredTermsDTO("t", List.of("core"), 90000L, "USD", 15, 4, "ok");
        String url = "/api/v1/deals/" + dealId + "/offers";

        mockMvc.perform(post(url)
                .header("Idempotency-Key", "dup")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OfferController.OfferRequest(terms, "msg"))))
                .andExpect(status().isOk());

        mockMvc.perform(post(url)
                .header("Idempotency-Key", "dup")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OfferController.OfferRequest(terms, "msg"))))
                .andExpect(status().isOk());

        List<Offer> offers = offerRepository.findByDealIdOrderByRoundNumberDesc(dealId);
        org.junit.jupiter.api.Assertions.assertEquals(1, offers.size());
    }

    private Mandate sampleMandate(UUID dealId, PartyRole role, UUID dummy) {
        return new Mandate(UUID.randomUUID(), dealId, role, 1, "USD", 120000L, 90000L, 20, 15, 2, 4, "[]", "[]", NegotiationMode.ASSISTED, 10, OffsetDateTime.now().plusYears(1));
    }
}
