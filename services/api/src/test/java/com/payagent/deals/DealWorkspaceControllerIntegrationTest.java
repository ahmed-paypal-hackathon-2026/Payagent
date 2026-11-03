package com.payagent.deals;

import com.payagent.agreements.AgreementService;
import com.payagent.auth.User;
import com.payagent.auth.UserRepository;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.disputes.model.CreateDisputeRequest;
import com.payagent.disputes.service.DisputeService;
import com.payagent.mandates.MandateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class DealWorkspaceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private MandateRepository mandateRepository;

    @Autowired
    private OfferRepository offerRepository;

    @Autowired
    private AgreementService agreementService;

    @Autowired
    private DisputeService disputeService;

    @Test
    void participantWorkspaceShowsOwnMandateOffersAgreementAndNoSettlement() throws Exception {
        UUID buyerId = userRepository.save(new User(UUID.randomUUID(), "buyer-ws-" + UUID.randomUUID() + "@t.dev", "pw", OffsetDateTime.now())).getId();
        UUID sellerId = userRepository.save(new User(UUID.randomUUID(), "seller-ws-" + UUID.randomUUID() + "@t.dev", "pw", OffsetDateTime.now())).getId();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyerId, sellerId, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(sampleMandate(dealId, PartyRole.BUYER));
        mandateRepository.save(sampleMandate(dealId, PartyRole.SELLER));

        StructuredTermsDTO offerTerms = new StructuredTermsDTO("design", List.of("core"), 95000L, "USD", 12, 2, "ok");
        offerRepository.save(new Offer(UUID.randomUUID(), dealId, 1, PartyRole.BUYER, false, offerTerms, "r", "0.8750", true));

        agreementService.proposeAgreement(dealId, new StructuredTermsDTO("design", List.of("core"), 90000L, "USD", 10, 2, "ok"));

        mockMvc.perform(get("/api/v1/deals/" + dealId + "/workspace")
                        .principal(() -> buyerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealId").value(dealId.toString()))
                .andExpect(jsonPath("$.status").value("TERMS_PENDING_ACCEPTANCE"))
                .andExpect(jsonPath("$.stopReason").value("NONE"))
                .andExpect(jsonPath("$.myRole").value("BUYER"))
                .andExpect(jsonPath("$.myMandate.partyRole").value("BUYER"))
                .andExpect(jsonPath("$.buyerEmail").isNotEmpty())
                .andExpect(jsonPath("$.sellerEmail").isNotEmpty())
                .andExpect(jsonPath("$.offers.length()").value(1))
                .andExpect(jsonPath("$.offers[0].roundNumber").value(1))
                .andExpect(jsonPath("$.offers[0].proposerRole").value("BUYER"))
                .andExpect(jsonPath("$.currentAgreement.version").value(1))
                .andExpect(jsonPath("$.settlement.invoiceStatus").value("NOT_CREATED"))
                .andExpect(jsonPath("$.settlement.paymentStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.evidenceList.length()").value(0))
                .andExpect(jsonPath("$.disputeStatus").value("NONE"));
    }

    @Test
    void workspaceExposesOpenDisputeStatusAndReason() throws Exception {
        User buyer = userRepository.save(new User(UUID.randomUUID(), "buyer-dw-" + UUID.randomUUID() + "@t.dev", "pw", OffsetDateTime.now()));
        UUID sellerId = userRepository.save(new User(UUID.randomUUID(), "seller-dw-" + UUID.randomUUID() + "@t.dev", "pw", OffsetDateTime.now())).getId();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer.getId(), sellerId, DealStatus.SETTLEMENT_PENDING));
        mandateRepository.save(sampleMandate(dealId, PartyRole.BUYER));
        mandateRepository.save(sampleMandate(dealId, PartyRole.SELLER));

        disputeService.createDispute(dealId, new CreateDisputeRequest("deliverable late", null), buyer.getEmail());

        mockMvc.perform(get("/api/v1/deals/" + dealId + "/workspace")
                        .principal(() -> buyer.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPUTED"))
                .andExpect(jsonPath("$.disputeStatus").value("OPEN"))
                .andExpect(jsonPath("$.disputeReason").value("deliverable late"));
    }

    @Test
    void outsideDealParticipantForbiddenOrNotFound() throws Exception {
        UUID buyerId = userRepository.save(new User(UUID.randomUUID(), "buyer-np-" + UUID.randomUUID() + "@t.dev", "pw", OffsetDateTime.now())).getId();
        UUID sellerId = userRepository.save(new User(UUID.randomUUID(), "seller-np-" + UUID.randomUUID() + "@t.dev", "pw", OffsetDateTime.now())).getId();
        UUID outsiderId = userRepository.save(new User(UUID.randomUUID(), "outsider-np-" + UUID.randomUUID() + "@t.dev", "pw", OffsetDateTime.now())).getId();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyerId, sellerId, DealStatus.ACTIVE_NEGOTIATION));

        mockMvc.perform(get("/api/v1/deals/" + dealId + "/workspace")
                        .principal(() -> outsiderId.toString()))
                .andExpect(status().isForbidden());
    }

    private com.payagent.mandates.Mandate sampleMandate(UUID dealId, PartyRole role) {
        return new com.payagent.mandates.Mandate(
                UUID.randomUUID(),
                dealId,
                role,
                1,
                "USD",
                role == PartyRole.BUYER ? 120000L : 90000L,
                role == PartyRole.BUYER ? 90000L : 120000L,
                role == PartyRole.BUYER ? 20 : 10,
                15,
                role == PartyRole.BUYER ? 2 : 3,
                role == PartyRole.BUYER ? 4 : 1,
                "[]",
                "[]",
                NegotiationMode.ASSISTED,
                10,
                OffsetDateTime.now().plusYears(1)
        );
    }
}
