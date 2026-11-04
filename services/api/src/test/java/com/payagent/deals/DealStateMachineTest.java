package com.payagent.deals;

import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.NegotiationStopReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DealStateMachineTest {

    @Autowired
    private DealStateMachine stateMachine;

    @Autowired
    private DealRepository repository;

    private Deal createDeal(DealStatus status) {
        Deal deal = new Deal(UUID.randomUUID(), UUID.randomUUID(), null, status);
        return repository.save(deal);
    }

    @Test
    void legalTransition_succeeds() {
        Deal deal = createDeal(DealStatus.DRAFT);
        Deal updated = stateMachine.transition(deal.getId(), DealStatus.INVITED, NegotiationStopReason.NONE);
        assertEquals(DealStatus.INVITED, updated.getStatus());
    }

    @Test
    void illegalTransition_throws() {
        Deal deal = createDeal(DealStatus.DRAFT);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> stateMachine.transition(deal.getId(), DealStatus.AGREED, NegotiationStopReason.NONE));
        assertTrue(ex.getMessage().contains("Illegal deal transition"));
        // status unchanged
        Deal persisted = repository.findById(deal.getId()).orElseThrow();
        assertEquals(DealStatus.DRAFT, persisted.getStatus());
    }

    @Test
    void sameStateTransition_isNoOp() {
        Deal deal = createDeal(DealStatus.ACTIVE_NEGOTIATION);
        Deal updated = stateMachine.transition(deal.getId(), DealStatus.ACTIVE_NEGOTIATION, NegotiationStopReason.NONE);
        assertEquals(DealStatus.ACTIVE_NEGOTIATION, updated.getStatus());
    }

    @Test
    void missingDeal_throws() {
        UUID randomId = UUID.randomUUID();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> stateMachine.transition(randomId, DealStatus.INVITED, NegotiationStopReason.NONE));
        assertTrue(ex.getMessage().contains("Deal not found"));
    }

    @Test
    void agreedToSettlementPending_legal() {
        Deal deal = createDeal(DealStatus.AGREED);
        Deal updated = stateMachine.transition(deal.getId(), DealStatus.SETTLEMENT_PENDING, NegotiationStopReason.NONE);
        assertEquals(DealStatus.SETTLEMENT_PENDING, updated.getStatus());
    }

    @Test
    void settlementPendingToInProgress_legal() {
        Deal deal = createDeal(DealStatus.SETTLEMENT_PENDING);
        Deal updated = stateMachine.transition(deal.getId(), DealStatus.IN_PROGRESS, NegotiationStopReason.NONE);
        assertEquals(DealStatus.IN_PROGRESS, updated.getStatus());
    }
}
