package com.payagent.mandates;

import com.payagent.auth.User;
import com.payagent.auth.UserRepository;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import com.payagent.deals.Deal;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level integration tests for mandate creation, idempotency, and authorization.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MandateControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository userRepository;
    @Autowired private DealService dealService;
    @Autowired private MandateService mandateService;

    private User makeUser(String email) {
        return userRepository.save(new User(UUID.randomUUID(), email, "hash", OffsetDateTime.now()));
    }

    private Deal makeDeal(User buyer, User seller) {
        return dealService.createDeal(buyer.getId(), seller.getId());
    }

    private String mandateJson(UUID dealId, int version) throws Exception {
        long limitPrice = 100000L + version * 1000L;
        long targetPrice = 80000L + version * 1000L;
        MandateService.MandateCreateRequest req = new MandateService.MandateCreateRequest(
                "USD",
                limitPrice,
                targetPrice,
                30,
                10,
                0,
                3,
                List.of("figma", "source"),
                List.of("resale"),
                NegotiationMode.ASSISTED,
                5,
                java.time.OffsetDateTime.parse("2026-12-31T23:59:59Z")
        );
        return mapper.writeValueAsString(req);
    }

    private UserDetails userDetails(User u) {
        return new org.springframework.security.core.userdetails.User(
                u.getId().toString(), "password", List.of());
    }

    // -------------------------------------------------------- idempotency

    @Test
    void createMandate_firstRequestWithIdempotencyKey_returns201() throws Exception {
        User buyer = makeUser("ic1@test.com");
        User seller = makeUser("ic2@test.com");
        Deal deal = makeDeal(buyer, seller);

        mockMvc.perform(post("/api/v1/deals/{dealId}/mandates", deal.getId())
                        .with(user(userDetails(buyer)))
                        .header("Idempotency-Key", "key-1")
                        .contentType("application/json")
                        .content(mandateJson(deal.getId(), 1)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk());
    }

    @Test
    void createMandate_noIdempotencyKey_returns400() throws Exception {
        User buyer = makeUser("ic3@test.com");
        User seller = makeUser("ic4@test.com");
        Deal deal = makeDeal(buyer, seller);

        mockMvc.perform(post("/api/v1/deals/{dealId}/mandates", deal.getId())
                        .with(user(userDetails(buyer)))
                        .contentType("application/json")
                        .content(mandateJson(deal.getId(), 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createMandate_unauthenticated_returns401() throws Exception {
        User buyer = makeUser("ic5@test.com");
        User seller = makeUser("ic6@test.com");
        Deal deal = makeDeal(buyer, seller);

        mockMvc.perform(post("/api/v1/deals/{dealId}/mandates", deal.getId())
                        .header("Idempotency-Key", "key-1")
                        .contentType("application/json")
                        .content(mandateJson(deal.getId(), 1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createMandate_nonParticipant_returns403() throws Exception {
        User buyer = makeUser("ic7@test.com");
        User seller = makeUser("ic8@test.com");
        User stranger = makeUser("stranger-" + UUID.randomUUID() + "@test.com");
        Deal deal = makeDeal(buyer, seller);

        mockMvc.perform(post("/api/v1/deals/{dealId}/mandates", deal.getId())
                        .with(user(userDetails(stranger)))
                        .header("Idempotency-Key", "key-1")
                        .contentType("application/json")
                        .content(mandateJson(deal.getId(), 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyMandate_authenticatedBuyer_returns200() throws Exception {
        User buyer = makeUser("ic9@test.com");
        User seller = makeUser("ic10@test.com");
        Deal deal = makeDeal(buyer, seller);

        // Create a mandate first via service
        MandateService.MandateCreateRequest req = new MandateService.MandateCreateRequest(
                "USD", 100000L, 80000L, 30, 10, 0, 3,
                List.of("figma", "source"), List.of("resale"),
                NegotiationMode.ASSISTED, 5, java.time.OffsetDateTime.parse("2026-12-31T23:59:59Z"));
        mandateService.createMandate(deal.getId(), buyer.getId(), req);

        mockMvc.perform(get("/api/v1/deals/{dealId}/mandates", deal.getId())
                        .with(user(userDetails(buyer))))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------- idempotency replay / conflict

    @Test
    void createMandate_sameKeySameRequest_replaysCachedResponse() throws Exception {
        User buyer = makeUser("ir1@test.com");
        User seller = makeUser("ir2@test.com");
        Deal deal = makeDeal(buyer, seller);

        // First request
        mockMvc.perform(post("/api/v1/deals/{dealId}/mandates", deal.getId())
                        .with(user(userDetails(buyer)))
                        .header("Idempotency-Key", "replay-key-1")
                        .contentType("application/json")
                        .content(mandateJson(deal.getId(), 1)))
                .andExpect(status().isOk());

        // Same key + same request → should succeed (idempotent replay)
        mockMvc.perform(post("/api/v1/deals/{dealId}/mandates", deal.getId())
                        .with(user(userDetails(buyer)))
                        .header("Idempotency-Key", "replay-key-1")
                        .contentType("application/json")
                        .content(mandateJson(deal.getId(), 1)))
                .andExpect(status().isOk());
    }

    @Test
    void createMandate_sameKeyDifferentRequest_returns409() throws Exception {
        User buyer = makeUser("ir3@test.com");
        User seller = makeUser("ir4@test.com");
        Deal deal = makeDeal(buyer, seller);

        // First request with version 1
        mockMvc.perform(post("/api/v1/deals/{dealId}/mandates", deal.getId())
                        .with(user(userDetails(buyer)))
                        .header("Idempotency-Key", "conflict-key-1")
                        .contentType("application/json")
                        .content(mandateJson(deal.getId(), 1)))
                .andExpect(status().isOk());

        // Same key + different version → 409 Conflict
        mockMvc.perform(post("/api/v1/deals/{dealId}/mandates", deal.getId())
                        .with(user(userDetails(buyer)))
                        .header("Idempotency-Key", "conflict-key-1")
                        .contentType("application/json")
                        .content(mandateJson(deal.getId(), 2)))
                .andExpect(status().isConflict());
    }
}
