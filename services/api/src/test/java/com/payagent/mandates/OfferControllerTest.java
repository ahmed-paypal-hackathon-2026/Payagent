package com.payagent.mandates;

import com.payagent.auth.User;
import com.payagent.auth.UserRepository;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.OfferDTO;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.deals.Deal;
import com.payagent.deals.DealRepository;
import com.payagent.deals.DealService;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Controller-level integration tests for the {@code POST /api/v1/deals/{id}/offers}
 * endpoint. Verifies that offers are validated against the caller's active mandate,
 * that the caller's utility score is computed deterministically, and that idempotency
 * and authorization semantics are correct.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OfferControllerTest {

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

    private Deal makeDeal(User buyer, User seller) {
        return dealRepository.save(new Deal(UUID.randomUUID(), buyer.getId(), seller.getId(), DealStatus.ACTIVE_NEGOTIATION));
    }

    private UserDetails userDetails(User u) {
        return new org.springframework.security.core.userdetails.User(
                u.getEmail(), "password", List.of());
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

    /**
     * Request body mirroring the controller's CreateOfferRequest record.
     */
    public record OfferCreateRequest(StructuredTermsDTO terms, String message) {}

    // -------------------------------------------------------- successful offer

    @Test
    void createOffer_compliantTerms_returns201() throws Exception {
        User buyer = makeUser("oco1@test.com");
        User seller = makeUser("oco2@test.com");
        Deal deal = makeDeal(buyer, seller);
        UUID dealId = deal.getId();

        // Buyer creates a compliant mandate (limit >= target for buyer)
        MandateDTO buyerMandate = new MandateDTO("m-1", dealId.toString(), PartyRole.BUYER, 1, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                DELIVERED, List.of("resale"),
                NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
        mandateService.createMandate(dealId, buyer.getId(), new MandateService.MandateCreateRequest(
                "USD", 100000L, 80000L, 30, 10, 0, 3,
                DELIVERED, List.of("resale"),
                NegotiationMode.ASSISTED, 5, OffsetDateTime.parse("2026-12-31T23:59:59Z")));

        // Compliant terms: price=80000 (at target), delivery=10 (at target), revisions=3 (at target)
        mockMvc.perform(post("/api/v1/deals/{dealId}/offers", dealId)
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "offer-key-1")
                        .contentType("application/json")
                        .content(offerJson(dealId, 80000, 10, 3, "Fair terms")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundNumber").value(1))
                .andExpect(jsonPath("$.proposerRole").value("BUYER"))
                .andExpect(jsonPath("$.mandateCompliant").value(true))
                .andExpect(jsonPath("$.callerUtilityScore").value("1.0000"));
    }

    @Test
    void createOffer_nonCompliantTerms_returns422() throws Exception {
        User buyer = makeUser("oco3@test.com");
        User seller = makeUser("oco4@test.com");
        Deal deal = makeDeal(buyer, seller);
        UUID dealId = deal.getId();

        MandateDTO buyerMandate = new MandateDTO("m-1", dealId.toString(), PartyRole.BUYER, 1, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                DELIVERED, List.of("resale"),
                NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
        mandateService.createMandate(dealId, buyer.getId(), new MandateService.MandateCreateRequest(
                "USD", 100000L, 80000L, 30, 10, 0, 3,
                DELIVERED, List.of("resale"),
                NegotiationMode.ASSISTED, 5, OffsetDateTime.parse("2026-12-31T23:59:59Z")));

        // Non-compliant: price=100001 exceeds buyer's limit of 100000
        mockMvc.perform(post("/api/v1/deals/{dealId}/offers", dealId)
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "offer-key-2")
                        .contentType("application/json")
                        .content(offerJson(dealId, 100001, 10, 3, "Over budget")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createOffer_unauthenticated_returns401() throws Exception {
        User buyer = makeUser("oco5@test.com");
        User seller = makeUser("oco6@test.com");
        Deal deal = makeDeal(buyer, seller);
        UUID dealId = deal.getId();

        mockMvc.perform(post("/api/v1/deals/{dealId}/offers", dealId)
                        .header("Idempotency-Key", "offer-key-3")
                        .contentType("application/json")
                        .content(offerJson(dealId, 80000, 10, 3, "No auth")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createOffer_nonParticipant_returns403() throws Exception {
        User buyer = makeUser("oco7@test.com");
        User seller = makeUser("oco8@test.com");
        User stranger = makeUser("stranger-oco@test.com");
        Deal deal = makeDeal(buyer, seller);
        UUID dealId = deal.getId();

        MandateDTO buyerMandate = new MandateDTO("m-1", dealId.toString(), PartyRole.BUYER, 1, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                DELIVERED, List.of("resale"),
                NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
        mandateService.createMandate(dealId, buyer.getId(), new MandateService.MandateCreateRequest(
                "USD", 100000L, 80000L, 30, 10, 0, 3,
                DELIVERED, List.of("resale"),
                NegotiationMode.ASSISTED, 5, OffsetDateTime.parse("2026-12-31T23:59:59Z")));

        mockMvc.perform(post("/api/v1/deals/{dealId}/offers", dealId)
                        .with(user(userDetails(stranger)))
                        .requestAttr("userId", stranger.getId())
                        .header("Idempotency-Key", "offer-key-4")
                        .contentType("application/json")
                        .content(offerJson(dealId, 80000, 10, 3, "Stranger")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createOffer_noIdempotencyKey_returns400() throws Exception {
        User buyer = makeUser("oco9@test.com");
        User seller = makeUser("oco10@test.com");
        Deal deal = makeDeal(buyer, seller);
        UUID dealId = deal.getId();

        MandateDTO buyerMandate = new MandateDTO("m-1", dealId.toString(), PartyRole.BUYER, 1, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                DELIVERED, List.of("resale"),
                NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
        mandateService.createMandate(dealId, buyer.getId(), new MandateService.MandateCreateRequest(
                "USD", 100000L, 80000L, 30, 10, 0, 3,
                DELIVERED, List.of("resale"),
                NegotiationMode.ASSISTED, 5, OffsetDateTime.parse("2026-12-31T23:59:59Z")));

        mockMvc.perform(post("/api/v1/deals/{dealId}/offers", dealId)
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .contentType("application/json")
                        .content(offerJson(dealId, 80000, 10, 3, "No key")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOffer_sameKeySameRequest_replays() throws Exception {
        User buyer = makeUser("oco11@test.com");
        User seller = makeUser("oco12@test.com");
        Deal deal = makeDeal(buyer, seller);
        UUID dealId = deal.getId();

        MandateDTO buyerMandate = new MandateDTO("m-1", dealId.toString(), PartyRole.BUYER, 1, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                DELIVERED, List.of("resale"),
                NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
        mandateService.createMandate(dealId, buyer.getId(), new MandateService.MandateCreateRequest(
                "USD", 100000L, 80000L, 30, 10, 0, 3,
                DELIVERED, List.of("resale"),
                NegotiationMode.ASSISTED, 5, OffsetDateTime.parse("2026-12-31T23:59:59Z")));

        // First request
        mockMvc.perform(post("/api/v1/deals/{dealId}/offers", dealId)
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "replay-offer-1")
                        .contentType("application/json")
                        .content(offerJson(dealId, 80000, 10, 3, "First")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundNumber").value(1));

        // Same key + same request → replay
        mockMvc.perform(post("/api/v1/deals/{dealId}/offers", dealId)
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "replay-offer-1")
                        .contentType("application/json")
                        .content(offerJson(dealId, 80000, 10, 3, "First")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundNumber").value(1));
    }

    @Test
    void createOffer_sameKeyDifferentRequest_returns409() throws Exception {
        User buyer = makeUser("oco13@test.com");
        User seller = makeUser("oco14@test.com");
        Deal deal = makeDeal(buyer, seller);
        UUID dealId = deal.getId();

        MandateDTO buyerMandate = new MandateDTO("m-1", dealId.toString(), PartyRole.BUYER, 1, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                DELIVERED, List.of("resale"),
                NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
        mandateService.createMandate(dealId, buyer.getId(), new MandateService.MandateCreateRequest(
                "USD", 100000L, 80000L, 30, 10, 0, 3,
                DELIVERED, List.of("resale"),
                NegotiationMode.ASSISTED, 5, OffsetDateTime.parse("2026-12-31T23:59:59Z")));

        // First request with terms A
        mockMvc.perform(post("/api/v1/deals/{dealId}/offers", dealId)
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "conflict-offer-1")
                        .contentType("application/json")
                        .content(offerJson(dealId, 80000, 10, 3, "Terms A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundNumber").value(1));

        // Same key + different terms → 409
        mockMvc.perform(post("/api/v1/deals/{dealId}/offers", dealId)
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "conflict-offer-1")
                        .contentType("application/json")
                        .content(offerJson(dealId, 85000, 12, 2, "Terms B")))
                .andExpect(status().isConflict());
    }

    @Test
    void createOffer_utilityScoreComputedCorrectly() throws Exception {
        User buyer = makeUser("oco15@test.com");
        User seller = makeUser("oco16@test.com");
        Deal deal = makeDeal(buyer, seller);
        UUID dealId = deal.getId();

        MandateDTO buyerMandate = new MandateDTO("m-1", dealId.toString(), PartyRole.BUYER, 1, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                DELIVERED, List.of("resale"),
                NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
        mandateService.createMandate(dealId, buyer.getId(), new MandateService.MandateCreateRequest(
                "USD", 100000L, 80000L, 30, 10, 0, 3,
                DELIVERED, List.of("resale"),
                NegotiationMode.ASSISTED, 5, OffsetDateTime.parse("2026-12-31T23:59:59Z")));

        // Terms at buyer target: price=80000, delivery=10, revisions=3
        // uP=1.0, uD=1.0, uR=1.0 → U_B = 0.6*1.0 + 0.25*1.0 + 0.15*1.0 = 1.0000
        mockMvc.perform(post("/api/v1/deals/{dealId}/offers", dealId)
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "utility-key-1")
                        .contentType("application/json")
                        .content(offerJson(dealId, 80000, 10, 3, "At target")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callerUtilityScore").value("1.0000"))
                .andExpect(jsonPath("$.mandateCompliant").value(true));
    }
}
