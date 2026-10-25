package com.payagent.settlement.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.common.contracts.AgreementSnapshotDTO;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.settlement.exception.SettlementForbiddenException;
import com.payagent.settlement.exception.SettlementNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(value = DealPort.class, ignored = DefaultDealPortAdapter.class)
public class DefaultDealPortAdapter implements DealPort {

    private static final Logger log = LoggerFactory.getLogger(DefaultDealPortAdapter.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DefaultDealPortAdapter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgreementSnapshotDTO getAgreedAgreement(UUID dealId) {
        String sql = """
                SELECT a.id, a.version, a.canonical_terms_json, a.terms_hash_sha256,
                       a.buyer_accepted_at, a.seller_accepted_at
                FROM agreements a
                WHERE a.deal_id = ?
                ORDER BY a.version DESC
                LIMIT 1
                """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, dealId);
        if (rows.isEmpty()) {
            throw new SettlementNotFoundException("No agreement snapshot found for deal " + dealId);
        }

        Map<String, Object> row = rows.get(0);
        try {
            String termsJson = String.valueOf(row.get("canonical_terms_json"));
            StructuredTermsDTO canonicalTerms = objectMapper.readValue(termsJson, StructuredTermsDTO.class);
            return new AgreementSnapshotDTO(
                    String.valueOf(row.get("id")),
                    dealId.toString(),
                    ((Number) row.get("version")).intValue(),
                    canonicalTerms,
                    String.valueOf(row.get("terms_hash_sha256")),
                    row.get("buyer_accepted_at") != null ? String.valueOf(row.get("buyer_accepted_at")) : null,
                    row.get("seller_accepted_at") != null ? String.valueOf(row.get("seller_accepted_at")) : null
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse canonical agreement terms for deal " + dealId, e);
        }
    }

    @Override
    public DealStatus getDealStatus(UUID dealId) {
        String sql = "SELECT status FROM deals WHERE id = ?";
        List<String> statuses = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("status"), dealId);
        if (statuses.isEmpty()) {
            throw new SettlementNotFoundException("Deal not found: " + dealId);
        }
        return DealStatus.valueOf(statuses.get(0));
    }

    @Override
    public void assertBuyer(UUID dealId, String callerEmail) {
        if (callerEmail == null || callerEmail.isBlank()) {
            return;
        }
        String buyerEmail = getBuyerEmail(dealId);
        if (!callerEmail.trim().equalsIgnoreCase(buyerEmail)) {
            throw new SettlementForbiddenException("Caller " + callerEmail + " is not the authorized BUYER for deal " + dealId);
        }
    }

    @Override
    public void assertParty(UUID dealId, String callerEmail) {
        if (callerEmail == null || callerEmail.isBlank()) {
            return;
        }
        String buyerEmail = getBuyerEmail(dealId);
        String sellerEmail = getSellerEmail(dealId);
        boolean isParty = callerEmail.trim().equalsIgnoreCase(buyerEmail) ||
                (sellerEmail != null && callerEmail.trim().equalsIgnoreCase(sellerEmail));
        if (!isParty) {
            throw new SettlementForbiddenException("Caller " + callerEmail + " is not a participant in deal " + dealId);
        }
    }

    @Override
    public void transitionDealStatus(UUID dealId, DealStatus targetStatus) {
        String sql = "UPDATE deals SET status = ?, updated_at = NOW() WHERE id = ?";
        int updated = jdbcTemplate.update(sql, targetStatus.name(), dealId);
        if (updated > 0) {
            log.info("Transitioned deal {} status to {}", dealId, targetStatus);
        } else {
            log.warn("Attempted to transition non-existent deal {} to {}", dealId, targetStatus);
        }
    }

    @Override
    public boolean isDealDisputed(UUID dealId) {
        try {
            DealStatus status = getDealStatus(dealId);
            if (status == DealStatus.DISPUTED) {
                return true;
            }
        } catch (Exception e) {
            // ignore if checking disputes table directly
        }

        String sql = "SELECT COUNT(*) FROM disputes WHERE deal_id = ? AND status IN ('OPEN', 'UNDER_PARTIES_REVIEW')";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, dealId);
        return count != null && count > 0;
    }

    @Override
    public String getBuyerEmail(UUID dealId) {
        String sql = """
                SELECT u.email
                FROM deals d
                JOIN users u ON d.buyer_user_id = u.id
                WHERE d.id = ?
                """;
        List<String> emails = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("email"), dealId);
        if (emails.isEmpty()) {
            throw new SettlementNotFoundException("Deal buyer not found for deal " + dealId);
        }
        return emails.get(0);
    }

    @Override
    public String getSellerEmail(UUID dealId) {
        String sql = """
                SELECT u.email
                FROM deals d
                LEFT JOIN users u ON d.seller_user_id = u.id
                WHERE d.id = ?
                """;
        List<String> emails = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("email"), dealId);
        return emails.isEmpty() ? null : emails.get(0);
    }
}
