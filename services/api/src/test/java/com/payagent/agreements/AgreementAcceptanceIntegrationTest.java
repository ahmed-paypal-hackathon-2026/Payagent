package com.payagent.agreements;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.deals.Deal;
import com.payagent.deals.DealRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class AgreementAcceptanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private MandateRepository mandateRepository;

    @Autowired
    private AgreementService agreementService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void proposeAndAcceptBothParties_dealBecomesAgreed() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(sampleMandate(dealId, PartyRole.BUYER, buyer));
        mandateRepository.save(sampleMandate(dealId, PartyRole.SELLER, seller));

        StructuredTermsDTO terms = new StructuredTermsDTO("t", List.of("core"), 90000L, "USD", 15, 4, "ok");
        String proposeUrl = "/api/v1/deals/" + dealId + "/agreements/propose";

        String proposeResponse = mockMvc.perform(post(proposeUrl)
                .header("Idempotency-Key", "prop-1")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(terms)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var snapshot = objectMapper.readValue(proposeResponse, com.payagent.common.contracts.AgreementSnapshotDTO.class);
        int version = snapshot.version();
        String hash = snapshot.termsHashSha256();

        String acceptUrl = "/api/v1/deals/" + dealId + "/agreements/" + version + "/accept";
        String acceptBody = objectMapper.writeValueAsString(new AcceptRequest(hash));

        mockMvc.perform(post(acceptUrl)
                .header("Idempotency-Key", "acc-buyer-1")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(acceptBody))
                .andExpect(status().isOk());

        mockMvc.perform(post(acceptUrl)
                .header("Idempotency-Key", "acc-seller-1")
                .principal(() -> seller.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(acceptBody))
                .andExpect(status().isOk());

        Deal updated = dealRepository.findById(dealId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(DealStatus.AGREED, updated.getStatus());
    }

    @Test
    void acceptStaleHash_returnsConflict() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(sampleMandate(dealId, PartyRole.BUYER, buyer));
        mandateRepository.save(sampleMandate(dealId, PartyRole.SELLER, seller));

        StructuredTermsDTO terms = new StructuredTermsDTO("t", List.of("core"), 90000L, "USD", 15, 4, "ok");
        String proposeUrl = "/api/v1/deals/" + dealId + "/agreements/propose";
        String proposeResponse = mockMvc.perform(post(proposeUrl)
                .header("Idempotency-Key", "prop-stale")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(terms)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var snapshot = objectMapper.readValue(proposeResponse, com.payagent.common.contracts.AgreementSnapshotDTO.class);
        int version = snapshot.version();

        String acceptUrl = "/api/v1/deals/" + dealId + "/agreements/" + version + "/accept";
        String badHash = "0".repeat(64);
        String acceptBody = objectMapper.writeValueAsString(new AcceptRequest(badHash));

        mockMvc.perform(post(acceptUrl)
                .header("Idempotency-Key", "acc-badhash")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(acceptBody))
                .andExpect(status().isConflict());
    }

    @Test
    void newVersionClearsPriorAcceptances() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(sampleMandate(dealId, PartyRole.BUYER, buyer));
        mandateRepository.save(sampleMandate(dealId, PartyRole.SELLER, seller));

        StructuredTermsDTO termsV1 = new StructuredTermsDTO("t", List.of("core"), 90000L, "USD", 15, 4, "ok");
        StructuredTermsDTO termsV2 = new StructuredTermsDTO("t", List.of("core"), 95000L, "USD", 15, 4, "ok");

        String proposeUrl = "/api/v1/deals/" + dealId + "/agreements/propose";
        String respV1 = mockMvc.perform(post(proposeUrl)
                .header("Idempotency-Key", "prop-v1")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(termsV1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var snapV1 = objectMapper.readValue(respV1, com.payagent.common.contracts.AgreementSnapshotDTO.class);

        String acceptUrlV1 = "/api/v1/deals/" + dealId + "/agreements/" + snapV1.version() + "/accept";
        String acceptBodyV1 = objectMapper.writeValueAsString(new AcceptRequest(snapV1.termsHashSha256()));
        mockMvc.perform(post(acceptUrlV1)
                .header("Idempotency-Key", "acc-v1-buyer")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(acceptBodyV1))
                .andExpect(status().isOk());

        String respV2 = mockMvc.perform(post(proposeUrl)
                .header("Idempotency-Key", "prop-v2")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(termsV2)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var snapV2 = objectMapper.readValue(respV2, com.payagent.common.contracts.AgreementSnapshotDTO.class);

        org.junit.jupiter.api.Assertions.assertNull(snapV2.buyerAcceptedAt());
        org.junit.jupiter.api.Assertions.assertNull(snapV2.sellerAcceptedAt());

        mockMvc.perform(post(acceptUrlV1)
                .header("Idempotency-Key", "acc-v1-again")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(acceptBodyV1))
                .andExpect(status().isConflict());
    }

    @Test
    void acceptNonExistentVersion_returnsNotFound() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(sampleMandate(dealId, PartyRole.BUYER, buyer));
        mandateRepository.save(sampleMandate(dealId, PartyRole.SELLER, seller));

        String acceptUrl = "/api/v1/deals/" + dealId + "/agreements/999/accept";
        String acceptBody = objectMapper.writeValueAsString(new AcceptRequest("0".repeat(64)));

        mockMvc.perform(post(acceptUrl)
                .header("Idempotency-Key", "acc-notfound")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(acceptBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void proposeNewVersionThenBothAcceptNewVersion_dealBecomesAgreed() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(sampleMandate(dealId, PartyRole.BUYER, buyer));
        mandateRepository.save(sampleMandate(dealId, PartyRole.SELLER, seller));

        StructuredTermsDTO termsV1 = new StructuredTermsDTO("t", List.of("core"), 90000L, "USD", 15, 4, "ok");
        StructuredTermsDTO termsV2 = new StructuredTermsDTO("t", List.of("core"), 95000L, "USD", 15, 4, "ok");

        String proposeUrl = "/api/v1/deals/" + dealId + "/agreements/propose";

        String respV1 = mockMvc.perform(post(proposeUrl)
                .header("Idempotency-Key", "prop-v1-2")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(termsV1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var snapV1 = objectMapper.readValue(respV1, com.payagent.common.contracts.AgreementSnapshotDTO.class);

        String respV2 = mockMvc.perform(post(proposeUrl)
                .header("Idempotency-Key", "prop-v2-2")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(termsV2)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var snapV2 = objectMapper.readValue(respV2, com.payagent.common.contracts.AgreementSnapshotDTO.class);

        String acceptUrlV2 = "/api/v1/deals/" + dealId + "/agreements/" + snapV2.version() + "/accept";
        String acceptBodyV2 = objectMapper.writeValueAsString(new AcceptRequest(snapV2.termsHashSha256()));

        mockMvc.perform(post(acceptUrlV2)
                .header("Idempotency-Key", "acc-v2-buyer")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(acceptBodyV2))
                .andExpect(status().isOk());

        mockMvc.perform(post(acceptUrlV2)
                .header("Idempotency-Key", "acc-v2-seller")
                .principal(() -> seller.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(acceptBodyV2))
                .andExpect(status().isOk());

        Deal updated = dealRepository.findById(dealId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(DealStatus.AGREED, updated.getStatus());
    }

    @Test
    void acceptWithInvalidPartyRole_returnsBadRequest() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(sampleMandate(dealId, PartyRole.BUYER, buyer));
        mandateRepository.save(sampleMandate(dealId, PartyRole.SELLER, seller));

        StructuredTermsDTO terms = new StructuredTermsDTO("t", List.of("core"), 90000L, "USD", 15, 4, "ok");
        String proposeUrl = "/api/v1/deals/" + dealId + "/agreements/propose";
        String proposeResponse = mockMvc.perform(post(proposeUrl)
                .header("Idempotency-Key", "prop-invalid-role")
                .principal(() -> buyer.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(terms)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var snapshot = objectMapper.readValue(proposeResponse, com.payagent.common.contracts.AgreementSnapshotDTO.class);
        int version = snapshot.version();

        // Direct service call with invalid role to test mapping
        org.junit.jupiter.api.Assertions.assertThrows(com.payagent.agreements.AgreementInvalidInputException.class, () -> {
            agreementService.acceptAgreement(dealId, version, snapshot.termsHashSha256(), "INVALID_ROLE");
        });
    }

    private Mandate sampleMandate(UUID dealId, PartyRole role, UUID dummy) {
        return new Mandate(UUID.randomUUID(), dealId, role, 1, "USD", 120000L, 90000L, 20, 15, 2, 4, "[]", "[]", com.payagent.common.contracts.NegotiationMode.ASSISTED, 10, OffsetDateTime.now().plusYears(1));
    }

    public record AcceptRequest(String termsHashSha256) {}
}
