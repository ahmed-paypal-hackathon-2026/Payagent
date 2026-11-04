package com.payagent.deals;

import com.payagent.agreements.Sha256Hasher;
import com.payagent.auth.User;
import com.payagent.auth.UserRepository;
import com.payagent.common.contracts.PartyRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DealServiceTest {

    @Autowired private DealService dealService;
    @Autowired private UserRepository userRepository;
    @Autowired private DealRepository dealRepository;

    private User makeUser(String email) {
        User u = new User(UUID.randomUUID(), email, "hash", java.time.OffsetDateTime.now());
        return userRepository.save(u);
    }

    @Test
    void createDeal_andMembership() {
        User buyer = makeUser("buyer@test.com");
        User seller = makeUser("seller@test.com");
        Deal deal = dealService.createDeal(buyer.getId(), seller.getId());
        assertNotNull(deal.getId());
        assertEquals(PartyRole.BUYER, dealService.partyRoleOf(deal.getId(), buyer.getId()));
        assertEquals(PartyRole.SELLER, dealService.partyRoleOf(deal.getId(), seller.getId()));
        assertNull(dealService.partyRoleOf(deal.getId(), UUID.randomUUID()));
    }

    @Test
    void requireMembership_throwsForNonParticipant() {
        User buyer = makeUser("b2@test.com");
        User seller = makeUser("s2@test.com");
        Deal deal = dealService.createDeal(buyer.getId(), seller.getId());
        assertThrows(SecurityException.class,
                () -> dealService.requireMembership(deal.getId(), UUID.randomUUID()));
    }

    @Test
    void requireMembership_throwsForUnknownDeal() {
        assertThrows(IllegalArgumentException.class,
                () -> dealService.requireMembership(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void setStatus_updatesStatus() {
        User buyer = makeUser("b3@test.com");
        User seller = makeUser("s3@test.com");
        Deal deal = dealService.createDeal(buyer.getId(), seller.getId());
        dealService.setStatus(deal.getId(), com.payagent.common.contracts.DealStatus.ACTIVE_NEGOTIATION);
        assertEquals(com.payagent.common.contracts.DealStatus.ACTIVE_NEGOTIATION,
                dealService.getDeal(deal.getId()).getStatus());
    }

    @Test
    void joinDeal_validToken_assignsSeller() {
        User buyer = makeUser("jbuyer@test.com");
        User seller = makeUser("jseller@test.com");
        Deal deal = dealService.createDeal(buyer.getId(), null);
        String token = "invite-123";
        String hash = Sha256Hasher.hash(token.getBytes(StandardCharsets.UTF_8));
        deal.setInviteTokenHash(hash);
        dealRepository.save(deal);

        Deal joined = dealService.joinDeal(deal.getId(), seller.getId(), token);
        assertEquals(seller.getId(), joined.getSellerUserId());
    }

    @Test
    void joinDeal_invalidToken_throws() {
        User buyer = makeUser("jbuyer2@test.com");
        User seller = makeUser("jseller2@test.com");
        Deal deal = dealService.createDeal(buyer.getId(), null);
        String hash = Sha256Hasher.hash("correct-token".getBytes(StandardCharsets.UTF_8));
        deal.setInviteTokenHash(hash);
        dealRepository.save(deal);

        assertThrows(IllegalArgumentException.class, () ->
                dealService.joinDeal(deal.getId(), seller.getId(), "wrong-token"));
        // Ensure seller not assigned
        Deal after = dealService.getDeal(deal.getId());
        assertNull(after.getSellerUserId());
    }

    @Test
    void joinDeal_sellerAlreadyAssigned_throwsConflict() {
        User buyer = makeUser("jbuyer3@test.com");
        User seller1 = makeUser("jseller3a@test.com");
        User seller2 = makeUser("jseller3b@test.com");
        Deal deal = dealService.createDeal(buyer.getId(), seller1.getId());
        String token = "token3";
        String hash = Sha256Hasher.hash(token.getBytes(StandardCharsets.UTF_8));
        deal.setInviteTokenHash(hash);
        dealRepository.save(deal);

        assertThrows(IllegalStateException.class, () ->
                dealService.joinDeal(deal.getId(), seller2.getId(), token));
        // Original seller remains
        Deal after = dealService.getDeal(deal.getId());
        assertEquals(seller1.getId(), after.getSellerUserId());
    }
}
