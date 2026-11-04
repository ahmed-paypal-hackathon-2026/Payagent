package com.payagent.deals;

import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.NegotiationStopReason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Guarded deal state machine.
 *
 * <p>Legal transitions are derived from the canonical contract (plan v2.docx Section 3.1)
 * and the deal lifecycle described in the execution plan. Arbitrary status assignment is
 * rejected; only {@link #transition(UUID, DealStatus, NegotiationStopReason)} may change
 * the deal status, and only along a legal edge.
 *
 * <p>Note on naming (ISSUE-004): plan v2.docx Section 2.2 refers to the post-convergence
 * state as {@code AGREEMENT_CANDIDATE}, while the canonical {@code DealStatus} enum defines
 * {@code TERMS_PENDING_ACCEPTANCE}. The state machine uses the canonical value
 * {@code TERMS_PENDING_ACCEPTANCE}; the discrepancy is recorded in the issues log and is
 * not silently resolved here.
 */
@Service
public class DealStateMachine {

    private static final Map<DealStatus, Set<DealStatus>> LEGAL_TRANSITIONS = new EnumMap<>(DealStatus.class);

    static {
        LEGAL_TRANSITIONS.put(DealStatus.DRAFT,
                Set.of(DealStatus.INVITED, DealStatus.ACTIVE_NEGOTIATION, DealStatus.CANCELLED));
        LEGAL_TRANSITIONS.put(DealStatus.INVITED,
                Set.of(DealStatus.ACTIVE_NEGOTIATION, DealStatus.CANCELLED));
        LEGAL_TRANSITIONS.put(DealStatus.ACTIVE_NEGOTIATION,
                Set.of(DealStatus.TERMS_PENDING_ACCEPTANCE, DealStatus.DISPUTED, DealStatus.CANCELLED));
        LEGAL_TRANSITIONS.put(DealStatus.TERMS_PENDING_ACCEPTANCE,
                Set.of(DealStatus.AGREED, DealStatus.DISPUTED, DealStatus.CANCELLED));
        LEGAL_TRANSITIONS.put(DealStatus.AGREED,
                Set.of(DealStatus.SETTLEMENT_PENDING, DealStatus.DISPUTED, DealStatus.CANCELLED));
        LEGAL_TRANSITIONS.put(DealStatus.SETTLEMENT_PENDING,
                Set.of(DealStatus.IN_PROGRESS, DealStatus.DISPUTED, DealStatus.CANCELLED));
        LEGAL_TRANSITIONS.put(DealStatus.IN_PROGRESS,
                Set.of(DealStatus.DELIVERABLES_SUBMITTED, DealStatus.DISPUTED, DealStatus.CANCELLED));
        LEGAL_TRANSITIONS.put(DealStatus.DELIVERABLES_SUBMITTED,
                Set.of(DealStatus.ACCEPTED, DealStatus.DISPUTED, DealStatus.CANCELLED));
        LEGAL_TRANSITIONS.put(DealStatus.ACCEPTED,
                Set.of(DealStatus.CLOSED, DealStatus.DISPUTED));
        LEGAL_TRANSITIONS.put(DealStatus.DISPUTED,
                Set.of(DealStatus.CLOSED, DealStatus.CANCELLED));
        // CLOSED and CANCELLED are terminal: no outgoing transitions.
    }

    private final DealRepository repository;

    public DealStateMachine(DealRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Deal getDeal(UUID dealId) {
        return repository.findById(dealId)
                .orElseThrow(() -> new IllegalArgumentException("Deal not found: " + dealId));
    }

    /**
     * Transition the deal to a new status if the edge is legal.
     *
     * @param dealId the deal
     * @param nextStatus the target status
     * @param stopReason the negotiation stop reason (required when entering a terminal or
     *                   stopped state; may be {@code null} for ordinary transitions)
     * @return the updated deal
     * @throws IllegalArgumentException if the deal does not exist or the transition is illegal
     */
    @Transactional
    public Deal transition(UUID dealId, DealStatus nextStatus, NegotiationStopReason stopReason) {
        Deal deal = getDeal(dealId);
        DealStatus current = deal.getStatus();

        // Same status is a no-op (idempotent).
        if (current == nextStatus) {
            deal.setStopReason(stopReason != null ? stopReason : NegotiationStopReason.NONE);
            return repository.save(deal);
        }

        Set<DealStatus> allowed = LEGAL_TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(nextStatus)) {
            throw new IllegalArgumentException(
                    "Illegal deal transition: " + current + " -> " + nextStatus);
        }

        deal.setStatus(nextStatus);
        deal.setStopReason(stopReason != null ? stopReason : NegotiationStopReason.NONE);
        return repository.save(deal);
    }

    /**
     * Check whether a transition is legal without performing it.
     */
    public boolean isLegalTransition(DealStatus from, DealStatus to) {
        if (from == to) return true;
        Set<DealStatus> allowed = LEGAL_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
}
