package com.payagent.deals;

import com.payagent.auth.User;
import com.payagent.auth.UserRepository;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.NegotiationStopReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DealStateMachineTest {

    @Autowired private DealStateMachine stateMachine;
    @Autowired private DealRepository dealRepository;
    @Autowired private UserRepository userRepository;

    private User makeUser(String email) {
        return userRepository.save(new User(UUID.randomUUID(), email, "hash", OffsetDateTime.now()));
    }

    private Deal makeDeal(UUID buyerId, UUID sellerId, DealStatus status) {
        Deal deal = new Deal(UUID.randomUUID(), buyerId, sellerId, status);
        return dealRepository.save(deal);
    }

    @Test
    void legalTransition_draftToActiveNegotiation() {
        User buyer = makeUser("ts1@test.com");
        User seller = makeUser("ts2@test.com");
        Deal deal = makeDeal(buyer.getId(), seller.getId(), DealStatus.DRAFT);

        Deal updated = stateMachine.transition(deal.getId(), DealStatus.ACTIVE_NEGOTIATION, null);
        assertEquals(DealStatus.ACTIVE_NEGOTIATION, updated.getStatus());
    }

    @Test
    void illegalTransition_draftToAgreed_rejected() {
        User buyer = makeUser("ts3@test.com");
        User seller = makeUser("ts4@test.com");
        Deal deal = makeDeal(buyer.getId(), seller.getId(), DealStatus.DRAFT);

        assertThrows(IllegalArgumentException.class,
                () -> stateMachine.transition(deal.getId(), DealStatus.AGREED, null));
    }

    @Test
    void transition_toSameStatus_isNoOp() {
        User buyer = makeUser("ts5@test.com");
        User seller = makeUser("ts6@test.com");
        Deal deal = makeDeal(buyer.getId(), seller.getId(), DealStatus.ACTIVE_NEGOTIATION);

        Deal updated = stateMachine.transition(deal.getId(), DealStatus.ACTIVE_NEGOTIATION, null);
        assertEquals(DealStatus.ACTIVE_NEGOTIATION, updated.getStatus());
    }

    @Test
    void fullNegotiationPath() {
        User buyer = makeUser("ts7@test.com");
        User seller = makeUser("ts8@test.com");
        Deal deal = makeDeal(buyer.getId(), seller.getId(), DealStatus.DRAFT);

        deal = stateMachine.transition(deal.getId(), DealStatus.INVITED, null);
        assertEquals(DealStatus.INVITED, deal.getStatus());

        deal = stateMachine.transition(deal.getId(), DealStatus.ACTIVE_NEGOTIATION, null);
        assertEquals(DealStatus.ACTIVE_NEGOTIATION, deal.getStatus());

        deal = stateMachine.transition(deal.getId(), DealStatus.TERMS_PENDING_ACCEPTANCE, null);
        assertEquals(DealStatus.TERMS_PENDING_ACCEPTANCE, deal.getStatus());

        deal = stateMachine.transition(deal.getId(), DealStatus.AGREED, null);
        assertEquals(DealStatus.AGREED, deal.getStatus());

        deal = stateMachine.transition(deal.getId(), DealStatus.SETTLEMENT_PENDING, null);
        assertEquals(DealStatus.SETTLEMENT_PENDING, deal.getStatus());

        deal = stateMachine.transition(deal.getId(), DealStatus.IN_PROGRESS, null);
        assertEquals(DealStatus.IN_PROGRESS, deal.getStatus());
    }

    @Test
    void disputeFromAnyState() {
        User buyer = makeUser("ts9@test.com");
        User seller = makeUser("ts10@test.com");
        Deal deal = makeDeal(buyer.getId(), seller.getId(), DealStatus.ACTIVE_NEGOTIATION);

        Deal disputed = stateMachine.transition(deal.getId(), DealStatus.DISPUTED,
                NegotiationStopReason.POLICY_VIOLATION_BLOCKED);
        assertEquals(DealStatus.DISPUTED, disputed.getStatus());
        assertEquals(NegotiationStopReason.POLICY_VIOLATION_BLOCKED, disputed.getStopReason());
    }

    @Test
    void cancelledIsTerminal() {
        User buyer = makeUser("ts11@test.com");
        User seller = makeUser("ts12@test.com");
        Deal deal = makeDeal(buyer.getId(), seller.getId(), DealStatus.DRAFT);

        deal = stateMachine.transition(deal.getId(), DealStatus.CANCELLED, null);
        assertEquals(DealStatus.CANCELLED, deal.getStatus());

        UUID cancelledDealId = deal.getId();
        assertThrows(IllegalArgumentException.class,
                () -> stateMachine.transition(cancelledDealId, DealStatus.DRAFT, null));
    }

    @Test
    void isLegalTransition_knownEdges() {
        assertTrue(stateMachine.isLegalTransition(DealStatus.DRAFT, DealStatus.ACTIVE_NEGOTIATION));
        assertTrue(stateMachine.isLegalTransition(DealStatus.ACTIVE_NEGOTIATION, DealStatus.TERMS_PENDING_ACCEPTANCE));
        assertTrue(stateMachine.isLegalTransition(DealStatus.TERMS_PENDING_ACCEPTANCE, DealStatus.AGREED));
        assertFalse(stateMachine.isLegalTransition(DealStatus.DRAFT, DealStatus.AGREED));
        assertFalse(stateMachine.isLegalTransition(DealStatus.CLOSED, DealStatus.DRAFT));
        assertTrue(stateMachine.isLegalTransition(DealStatus.ACTIVE_NEGOTIATION, DealStatus.ACTIVE_NEGOTIATION));
    }
}
