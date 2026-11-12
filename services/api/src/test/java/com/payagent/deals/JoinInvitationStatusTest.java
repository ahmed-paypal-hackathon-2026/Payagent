package com.payagent.deals;

import com.payagent.agreements.Sha256Hasher;
import com.payagent.common.contracts.DealStatus;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class JoinInvitationStatusTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DealRepository dealRepository;

    private Deal invitedDeal(UUID buyerId, String token) {
        Deal deal = new Deal(UUID.randomUUID(), buyerId, null, DealStatus.INVITED);
        deal.setInviteTokenHash(Sha256Hasher.hash(token.getBytes(StandardCharsets.UTF_8)));
        return dealRepository.save(deal);
    }

    private String joinBody(String token) {
        return "{\"inviteToken\":\"" + token + "\"}";
    }

    @Test
    void invalidInvitationToken_returnsBadRequestWithoutMutatingSeller() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        Deal deal = invitedDeal(buyer, "correct-token");

        mockMvc.perform(post("/api/v1/deals/{id}/join", deal.getId())
                        .header("Idempotency-Key", "join-invalid-" + deal.getId())
                        .principal(() -> seller.toString())
                        .contentType("application/json")
                        .content(joinBody("wrong-token")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatorCannotJoin_returnsForbidden() throws Exception {
        UUID buyer = UUID.randomUUID();
        Deal deal = invitedDeal(buyer, "creator-token");

        mockMvc.perform(post("/api/v1/deals/{id}/join", deal.getId())
                        .header("Idempotency-Key", "join-creator-" + deal.getId())
                        .principal(() -> buyer.toString())
                        .contentType("application/json")
                        .content(joinBody("creator-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void existingSellerCannotBeReplaced_returnsConflict() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID firstSeller = UUID.randomUUID();
        UUID secondSeller = UUID.randomUUID();
        Deal deal = invitedDeal(buyer, "replace-token");
        deal.setSellerUserId(firstSeller);
        dealRepository.save(deal);

        mockMvc.perform(post("/api/v1/deals/{id}/join", deal.getId())
                        .header("Idempotency-Key", "join-replace-" + deal.getId())
                        .principal(() -> secondSeller.toString())
                        .contentType("application/json")
                        .content(joinBody("replace-token")))
                .andExpect(status().isConflict());
    }

    @Test
    void validInvitationToken_assignsSeller() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        Deal deal = invitedDeal(buyer, "valid-token");

        mockMvc.perform(post("/api/v1/deals/{id}/join", deal.getId())
                        .header("Idempotency-Key", "join-valid-" + deal.getId())
                        .principal(() -> seller.toString())
                        .contentType("application/json")
                        .content(joinBody("valid-token")))
                .andExpect(status().isOk());

        Deal after = dealRepository.findById(deal.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(seller, after.getSellerUserId());
    }
}
