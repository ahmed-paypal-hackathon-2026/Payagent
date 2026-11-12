package com.payagent.deals;

import com.payagent.agreements.Sha256Hasher;
import com.payagent.common.contracts.NegotiationStopReason;
import com.payagent.common.contracts.PartyRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
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
     * Loads the deal with a pessimistic write lock to serialize per-deal mutations
     * (e.g. mandate/agreement version allocation, dual-consent acceptance).
     */
    @Transactional
    public Deal getDealForUpdate(UUID dealId) {
        return repository.findByIdForUpdate(dealId)
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
    public Deal createDealWithInvite(UUID buyerUserId, String inviteToken) {
        if (buyerUserId == null) {
            throw new IllegalArgumentException("Buyer user id is required");
        }
        if (inviteToken == null || inviteToken.isBlank()) {
            throw new IllegalArgumentException("Invite token is required");
        }
        String hash = Sha256Hasher.hash(inviteToken.getBytes(StandardCharsets.UTF_8));
        Deal deal = new Deal(java.util.UUID.randomUUID(), buyerUserId, null,
                com.payagent.common.contracts.DealStatus.INVITED);
        deal.setInviteTokenHash(hash);
        return repository.save(deal);
    }

    @Transactional
    public Deal joinDeal(UUID dealId, UUID userId, String inviteToken) {        if (inviteToken == null || inviteToken.isBlank()) {
            throw new IllegalArgumentException("Invite token is required");
        }
        Deal deal = repository.findByIdForUpdate(dealId)
                .orElseThrow(() -> new IllegalArgumentException("Deal not found: " + dealId));
        if (userId.equals(deal.getBuyerUserId())) {
            throw new SecurityException("Creator cannot join as seller");
        }
        String tokenHash = Sha256Hasher.hash(inviteToken.getBytes(StandardCharsets.UTF_8));
        String storedHash = deal.getInviteTokenHash();
        if (storedHash == null || !storedHash.equalsIgnoreCase(tokenHash)) {
            throw new IllegalArgumentException("Invalid invitation token");
        }
        if (deal.getSellerUserId() != null) {
            if (!deal.getSellerUserId().equals(userId)) {
                throw new IllegalStateException("Seller already assigned and cannot be replaced");
            }
            // Idempotent: seller already assigned to same user
            return deal;
        }
        deal.setSellerUserId(userId);
        Deal saved = repository.save(deal);
        if (saved.getStatus() == com.payagent.common.contracts.DealStatus.INVITED) {
            stateMachine.transition(saved.getId(), com.payagent.common.contracts.DealStatus.ACTIVE_NEGOTIATION,
                    com.payagent.common.contracts.NegotiationStopReason.NONE);
        }
        return saved;
    }

    @Transactional
    public void setStatus(UUID dealId, com.payagent.common.contracts.DealStatus status) {
        stateMachine.transition(dealId, status, NegotiationStopReason.NONE);
    }
}
