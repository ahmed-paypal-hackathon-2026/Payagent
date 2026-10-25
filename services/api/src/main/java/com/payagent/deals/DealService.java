package com.payagent.deals;

import com.payagent.common.contracts.NegotiationStopReason;
import com.payagent.common.contracts.PartyRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DealService {

    private final DealRepository repository;
    private final DealStateMachine stateMachine;

    public DealService(DealRepository repository, DealStateMachine stateMachine) {
        this.repository = repository;
        this.stateMachine = stateMachine;
    }

    @Transactional(readOnly = true)
    public Deal getDeal(UUID dealId) {
        return repository.findById(dealId)
                .orElseThrow(() -> new IllegalArgumentException("Deal not found: " + dealId));
    }

    /**
     * Returns the party role of the given user on the deal, or null if the user is not a participant.
     * Server-derived only; never trust a client-supplied role.
     */
    @Transactional(readOnly = true)
    public PartyRole partyRoleOf(UUID dealId, UUID userId) {
        Deal deal = getDeal(dealId);
        return deal.partyRoleOf(userId);
    }

    /**
     * Verifies deal membership and returns the role; throws if the user is not a participant.
     */
    @Transactional(readOnly = true)
    public PartyRole requireMembership(UUID dealId, UUID userId) {
        PartyRole role = partyRoleOf(dealId, userId);
        if (role == null) {
            throw new SecurityException("User " + userId + " is not a participant of deal " + dealId);
        }
        return role;
    }

    @Transactional
    public Deal createDeal(UUID buyerUserId, UUID sellerUserId) {
        Deal deal = new Deal(java.util.UUID.randomUUID(), buyerUserId, sellerUserId,
                com.payagent.common.contracts.DealStatus.DRAFT);
        return repository.save(deal);
    }

    @Transactional
    public void setStatus(UUID dealId, com.payagent.common.contracts.DealStatus status) {
        stateMachine.transition(dealId, status, NegotiationStopReason.NONE);
    }
}
