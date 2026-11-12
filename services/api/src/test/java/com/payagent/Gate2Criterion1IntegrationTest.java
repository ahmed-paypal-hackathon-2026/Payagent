package com.payagent;

import com.payagent.auth.User;
import com.payagent.auth.UserRepository;
import com.payagent.common.contracts.AgreementSnapshotDTO;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.OfferDTO;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.deals.Deal;
import com.payagent.deals.DealRepository;
import com.payagent.deals.DealService;
import com.payagent.mandates.MandateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate 2 Criterion 1: Two users negotiate and lock identical termsHashSha256 through the REST API.
 *
 * Exercises the full real application flow with two distinct authenticated participants:
 * 1. Create deal + mandates via REST API
 * 2. Submit offers through REST API as both participants
 * 3. Propose agreement via REST API
 * 4. Accept as buyer and seller via REST API
 * 5. Verify deal transitions to AGREED
 * 6. Verify both parties resolve to identical termsHashSha256
 * 7. Verify unauthorized users cannot participate
 * 8. Verify invalid terms cannot be accepted
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Gate2Criterion1IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository userRepository;
    @Autowired private DealService dealService;
    @Autowired private DealRepository dealRepository;
    @Autowired private MandateService mandateService;

    private static final List<String> DELIVERED = List.of("figma", "source", "notes");

    private User makeUser(String email) {
        return userRepository.save(new User(UUID.randomUUID(), email, "hash", OffsetDateTime.now()));
    }

    private UserDetails userDetails(User u) {
        return new org.springframework.security.core.userdetails.User(
                u.getId().toString(), "password", List.of());
    }

    private String mandateJson(UUID dealId, PartyRole role, int version) {
        if (role == PartyRole.BUYER) {
            return mandateJson(dealId, version, role, 100000L, 80000L, 30, 10, 0, 3);
        } else {
            return mandateJson(dealId, version, role, 70000L, 100000L, 10, 30, 5, 1);
        }
    }

    private String mandateJson(UUID dealId, int version, PartyRole role,
                               long limitPrice, long targetPrice,
                               int limitDelivery, int targetDelivery,
                               int limitRevisions, int targetRevisions) {
        MandateService.MandateCreateRequest req = new MandateService.MandateCreateRequest(
                "USD",
                limitPrice,
                targetPrice,
                limitDelivery,
                targetDelivery,
                limitRevisions,
                targetRevisions,
                DELIVERED,
                List.of("resale"),
                NegotiationMode.ASSISTED,
                5,
                OffsetDateTime.parse("2026-12-31T23:59:59Z")
        );
        try {
            return mapper.writeValueAsString(req);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String offerJson(UUID dealId, long price, int delivery, int revisions, String message) {
        StructuredTermsDTO terms = new StructuredTermsDTO("Design sprint", DELIVERED, price, "USD",
                delivery, revisions, "Lighthouse > 90");
        try {
            return mapper.writeValueAsString(new OfferCreateRequest(terms, message));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String acceptanceJson(String termsHashSha256) {
        try {
            return mapper.writeValueAsString(new AcceptanceRequest(termsHashSha256));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public record OfferCreateRequest(StructuredTermsDTO terms, String message) {}
    public record AcceptanceRequest(String termsHashSha256) {}

    // -------------------------------------------------------- Criterion 1: Full negotiation flow

    @Test
    void twoUsersNegotiateAndLockIdenticalTermsHash() throws Exception {
        // 1. Create deal with buyer and seller
        User buyer = makeUser("gate2-buyer@test.com");
        User seller = makeUser("gate2-seller@test.com");
        Deal deal = dealRepository.save(new Deal(UUID.randomUUID(), buyer.getId(), seller.getId(), DealStatus.ACTIVE_NEGOTIATION));
        UUID dealId = deal.getId();
        assertEquals(DealStatus.ACTIVE_NEGOTIATION, deal.getStatus());

        // 2. Buyer creates mandate via REST API
        mockMvc.perform(post("/api/v1/deals/{dealId}/mandates", dealId)
                        .with(user(userDetails(buyer)))
                        .header("Idempotency-Key", "mandate-buyer-1")
                        .contentType("application/json")
                        .content(mandateJson(dealId, PartyRole.BUYER, 1)))
                .andExpect(status().isOk());

        // 3. Seller creates mandate via REST API
        mockMvc.perform(post("/api/v1/deals/{dealId}/mandates", dealId)
                        .with(user(userDetails(seller)))
                        .header("Idempotency-Key", "mandate-seller-1")
                        .contentType("application/json")
                        .content(mandateJson(dealId, PartyRole.SELLER, 1)))
                .andExpect(status().isOk());

        // 4. Buyer submits offer via REST API
        String offerResponse = mockMvc.perform(post("/api/v1/deals/{dealId}/offers", dealId)
                        .with(user(userDetails(buyer)))
                        .header("Idempotency-Key", "offer-buyer-1")
                        .contentType("application/json")
                        .content(offerJson(dealId, 80000, 10, 3, "Fair opening offer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundNumber").value(1))
                .andExpect(jsonPath("$.proposerRole").value("BUYER"))
                .andExpect(jsonPath("$.mandateCompliant").value(true))
                .andExpect(jsonPath("$.callerUtilityScore").value("1.0000"))
                .andReturn().getResponse().getContentAsString();

        OfferDTO buyerOffer = mapper.readValue(offerResponse, OfferDTO.class);
        assertEquals(PartyRole.BUYER, buyerOffer.proposerRole());
        assertTrue(buyerOffer.mandateCompliant());

        // 5. Propose agreement via REST API
        String proposeResponse = mockMvc.perform(post("/api/v1/deals/{dealId}/agreements/propose", dealId)
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "propose-1")
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(buyerOffer.terms())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.termsHashSha256").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        AgreementSnapshotDTO proposal = mapper.readValue(proposeResponse, AgreementSnapshotDTO.class);
        String termsHashSha256 = proposal.termsHashSha256();
        assertNotNull(termsHashSha256);
        assertEquals(64, termsHashSha256.length());
        assertNull(proposal.buyerAcceptedAt());
        assertNull(proposal.sellerAcceptedAt());

        assertEquals(DealStatus.TERMS_PENDING_ACCEPTANCE, dealService.getDeal(dealId).getStatus());

        // 6. Buyer accepts via REST API
        mockMvc.perform(post("/api/v1/deals/{dealId}/agreements/{version}/accept", dealId, 1)
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "accept-buyer-1")
                        .contentType("application/json")
                        .content(acceptanceJson(termsHashSha256)))
                .andExpect(status().isOk());

        assertEquals(DealStatus.TERMS_PENDING_ACCEPTANCE, dealService.getDeal(dealId).getStatus());

        // 7. Seller accepts via REST API -> deal transitions to AGREED
        String acceptResponse = mockMvc.perform(post("/api/v1/deals/{dealId}/agreements/{version}/accept", dealId, 1)
                        .with(user(userDetails(seller)))
                        .requestAttr("userId", seller.getId())
                        .header("Idempotency-Key", "accept-seller-1")
                        .contentType("application/json")
                        .content(acceptanceJson(termsHashSha256)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        AgreementSnapshotDTO finalSnapshot = mapper.readValue(acceptResponse, AgreementSnapshotDTO.class);

        // 8. Verify deal is AGREED
        assertEquals(DealStatus.AGREED, dealService.getDeal(dealId).getStatus());

        // 9. Verify both parties resolved to identical termsHashSha256
        assertEquals(termsHashSha256, finalSnapshot.termsHashSha256());
        assertEquals(termsHashSha256, proposal.termsHashSha256());
        assertNotNull(finalSnapshot.buyerAcceptedAt());
        assertNotNull(finalSnapshot.sellerAcceptedAt());
    }

    @Test
    void unauthorizedUserCannotParticipate() throws Exception {
        User buyer = makeUser("unauth-b@test.com");
        User seller = makeUser("unauth-s@test.com");
        User outsider = makeUser("unauth-outsider@test.com");
        Deal deal = dealRepository.save(new Deal(UUID.randomUUID(), buyer.getId(), seller.getId(), DealStatus.ACTIVE_NEGOTIATION));
        UUID dealId = deal.getId();

        MandateService.MandateCreateRequest req = new MandateService.MandateCreateRequest(
                "USD",
                100000L,
                80000L,
                30,
                10,
                0,
                3,
                DELIVERED,
                List.of("resale"),
                NegotiationMode.ASSISTED,
                5,
                OffsetDateTime.parse("2026-12-31T23:59:59Z")
        );
        mandateService.createMandate(dealId, buyer.getId(), req);

        mockMvc.perform(get("/api/v1/deals/{dealId}/mandates", dealId)
                        .with(user(userDetails(outsider)))
                        .requestAttr("userId", outsider.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/deals/{dealId}/offers", dealId)
                        .with(user(userDetails(outsider)))
                        .header("Idempotency-Key", "offer-outsider-1")
                        .contentType("application/json")
                        .content(offerJson(dealId, 80000, 10, 3, "Outsider offer")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidTermsCannotBeAccepted() throws Exception {
        User buyer = makeUser("invalid-b@test.com");
        User seller = makeUser("invalid-s@test.com");
        Deal deal = dealRepository.save(new Deal(UUID.randomUUID(), buyer.getId(), seller.getId(), DealStatus.ACTIVE_NEGOTIATION));
        UUID dealId = deal.getId();
        dealService.setStatus(dealId, DealStatus.ACTIVE_NEGOTIATION);

        MandateService.MandateCreateRequest req = new MandateService.MandateCreateRequest(
                "USD",
                100000L,
                80000L,
                30,
                10,
                0,
                3,
                DELIVERED,
                List.of("resale"),
                NegotiationMode.ASSISTED,
                5,
                OffsetDateTime.parse("2026-12-31T23:59:59Z")
        );
        mandateService.createMandate(dealId, buyer.getId(), req);

        mockMvc.perform(post("/api/v1/deals/{dealId}/offers", dealId)
                        .with(user(userDetails(buyer)))
                        .header("Idempotency-Key", "offer-invalid-1")
                        .contentType("application/json")
                        .content(offerJson(dealId, 100001, 10, 3, "Over budget")))
                .andExpect(status().isUnprocessableEntity());

        assertEquals(DealStatus.ACTIVE_NEGOTIATION, dealService.getDeal(dealId).getStatus());
    }
}
