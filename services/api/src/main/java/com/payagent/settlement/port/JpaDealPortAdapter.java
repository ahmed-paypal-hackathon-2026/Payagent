package com.payagent.settlement.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.agreements.Agreement;
import com.payagent.agreements.AgreementRepository;
import com.payagent.auth.User;
import com.payagent.auth.UserRepository;
import com.payagent.common.contracts.AgreementSnapshotDTO;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.NegotiationStopReason;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.deals.Deal;
import com.payagent.deals.DealRepository;
import com.payagent.deals.DealStateMachine;
import com.payagent.settlement.exception.SettlementForbiddenException;
import com.payagent.settlement.exception.SettlementNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class JpaDealPortAdapter implements DealPort {

    private final DealRepository dealRepository;
    private final AgreementRepository agreementRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final DealStateMachine stateMachine;

    public JpaDealPortAdapter(DealRepository dealRepository, AgreementRepository agreementRepository, UserRepository userRepository, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate, DealStateMachine stateMachine) {
        this.dealRepository = dealRepository;
        this.agreementRepository = agreementRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.stateMachine = stateMachine;
    }

    @Override
    public AgreementSnapshotDTO getAgreedAgreement(UUID dealId) {
        Agreement agreement = agreementRepository.findTopByDealIdOrderByVersionDesc(dealId)
                .orElseThrow(() -> new SettlementNotFoundException("No agreement snapshot found for deal " + dealId));
        try {
            StructuredTermsDTO terms = objectMapper.readValue(agreement.getCanonicalTermsJson(), StructuredTermsDTO.class);
            return new AgreementSnapshotDTO(
                    agreement.getId().toString(),
                    dealId.toString(),
                    agreement.getVersion(),
                    terms,
                    agreement.getTermsHashSha256(),
                    agreement.getBuyerAcceptedAt() != null ? agreement.getBuyerAcceptedAt().toString() : null,
                    agreement.getSellerAcceptedAt() != null ? agreement.getSellerAcceptedAt().toString() : null
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse canonical agreement terms for deal " + dealId, e);
        }
    }

    @Override
    public DealStatus getDealStatus(UUID dealId) {
        Deal deal = dealRepository.findById(dealId)
                .orElseThrow(() -> new SettlementNotFoundException("Deal not found: " + dealId));
        return deal.getStatus();
    }

    @Override
    public void assertBuyer(UUID dealId, String callerEmail) {
        String resolved = resolveCallerEmail(dealId, callerEmail);
        String buyerEmail = getBuyerEmail(dealId);
        if (!resolved.equalsIgnoreCase(buyerEmail)) {
            throw new SettlementForbiddenException("Caller " + resolved + " is not the authorized BUYER for deal " + dealId);
        }
    }

    @Override
    public void assertSeller(UUID dealId, String callerEmail) {
        String resolved = resolveCallerEmail(dealId, callerEmail);
        String sellerEmail = getSellerEmail(dealId);
        if (sellerEmail == null || !resolved.equalsIgnoreCase(sellerEmail)) {
            throw new SettlementForbiddenException("Caller " + resolved + " is not the authorized SELLER for deal " + dealId);
        }
    }

    @Override
    public void assertParty(UUID dealId, String callerEmail) {
        String resolved = resolveCallerEmail(dealId, callerEmail);
        String buyerEmail = getBuyerEmail(dealId);
        String sellerEmail = getSellerEmail(dealId);
        boolean isParty = resolved.equalsIgnoreCase(buyerEmail) || (sellerEmail != null && resolved.equalsIgnoreCase(sellerEmail));
        if (!isParty) {
            throw new SettlementForbiddenException("Caller " + resolved + " is not a participant in deal " + dealId);
        }
    }

    @Override
    public void transitionDealStatus(UUID dealId, DealStatus targetStatus) {
        stateMachine.transition(dealId, targetStatus, NegotiationStopReason.NONE);
    }

    @Override
    public DealStatus lockDealAndGetStatus(UUID dealId) {
        String status;
        try {
            status = jdbcTemplate.queryForObject(
                    "SELECT status FROM deals WHERE id = ? FOR UPDATE", String.class, dealId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new SettlementNotFoundException("Deal not found: " + dealId);
        }
        return status != null ? DealStatus.valueOf(status) : null;
    }

    @Override
    public boolean hasOpenDispute(UUID dealId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM disputes WHERE deal_id = ? AND status IN ('OPEN', 'UNDER_PARTIES_REVIEW')",
                    Integer.class, dealId);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isDealDisputed(UUID dealId) {
        Deal deal = dealRepository.findById(dealId).orElse(null);
        if (deal != null && deal.getStatus() == DealStatus.DISPUTED) {
            return true;
        }
        return hasOpenDispute(dealId);
    }

    @Override
    public String getBuyerEmail(UUID dealId) {
        Deal deal = dealRepository.findById(dealId)
                .orElseThrow(() -> new SettlementNotFoundException("Deal not found: " + dealId));
        UUID buyerId = deal.getBuyerUserId();
        User user = userRepository.findById(buyerId)
                .orElseThrow(() -> new SettlementNotFoundException("Deal buyer not found for deal " + dealId));
        return user.getEmail();
    }

    /**
     * Resolves the authenticated caller identity to a canonical email.
     * The HTTP principal is the user's UUID, while service code compares
     * emails. Accepting both keeps the comparison consistent and fail-closed.
     */
    private String resolveCallerEmail(UUID dealId, String caller) {
        if (caller == null || caller.isBlank()) {
            throw new SettlementForbiddenException("Authentication required for deal " + dealId);
        }
        String trimmed = caller.trim();
        try {
            UUID userId = UUID.fromString(trimmed);
            return userRepository.findById(userId)
                    .map(User::getEmail)
                    .orElseThrow(() -> new SettlementForbiddenException("Authenticated user not found: " + userId));
        } catch (IllegalArgumentException e) {
            // Not a UUID textual principal: treat as email.
            return trimmed;
        }
    }

    @Override
    public String getSellerEmail(UUID dealId) {
        Deal deal = dealRepository.findById(dealId)
                .orElseThrow(() -> new SettlementNotFoundException("Deal not found: " + dealId));
        UUID sellerId = deal.getSellerUserId();
        if (sellerId == null) return null;
        return userRepository.findById(sellerId).map(User::getEmail).orElse(null);
    }
}
