package com.payagent.disputes.service;

import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.DisputeStatus;
import com.payagent.disputes.model.CreateDisputeRequest;
import com.payagent.disputes.model.DisputeDTO;
import com.payagent.disputes.model.ResolveDisputeRequest;
import com.payagent.disputes.persistence.DisputeEntity;
import com.payagent.disputes.persistence.DisputeRepository;
import com.payagent.settlement.exception.SettlementConflictException;
import com.payagent.settlement.exception.SettlementNotFoundException;
import com.payagent.settlement.port.AuditEventPort;
import com.payagent.settlement.port.DealPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DisputeService {

    private static final Logger log = LoggerFactory.getLogger(DisputeService.class);

    private final DisputeRepository disputeRepository;
    private final DealPort dealPort;
    private final AuditEventPort auditEventPort;
    private final JdbcTemplate jdbcTemplate;

    public DisputeService(
            DisputeRepository disputeRepository,
            DealPort dealPort,
            AuditEventPort auditEventPort,
            JdbcTemplate jdbcTemplate) {
        this.disputeRepository = disputeRepository;
        this.dealPort = dealPort;
        this.auditEventPort = auditEventPort;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public DisputeDTO createDispute(UUID dealId, CreateDisputeRequest request, String callerEmail) {
        dealPort.assertParty(dealId, callerEmail);

        DealStatus currentStatus = dealPort.getDealStatus(dealId);
        if (currentStatus == DealStatus.CLOSED || currentStatus == DealStatus.CANCELLED) {
            throw new SettlementConflictException("Cannot open dispute on deal " + dealId + " in " + currentStatus + " status");
        }

        UUID disputeId = UUID.randomUUID();
        DisputeEntity entity = new DisputeEntity(
                disputeId,
                dealId,
                DisputeStatus.OPEN,
                request.reason(),
                request.resolutionRequest()
        );
        disputeRepository.save(entity);

        // Invariant: Immediately transition DealStatus -> DISPUTED to block automated financial actions
        dealPort.transitionDealStatus(dealId, DealStatus.DISPUTED);

        // Update stop_reason to USER_PAUSED in deals table if deals table exists
        try {
            String updateStopReasonSql = "UPDATE deals SET stop_reason = 'USER_PAUSED', updated_at = NOW() WHERE id = ?";
            jdbcTemplate.update(updateStopReasonSql, dealId);
        } catch (Exception e) {
            log.warn("Could not update stop_reason in deals table for deal {}: {}", dealId, e.getMessage());
        }

        log.warn("Deal {} entered DISPUTED status. Automated capture/financial actions paused. DisputeId={}", dealId, disputeId);

        // Record audit event for dispute opening
        auditEventPort.recordEvent(dealId, "DISPUTE_OPENED", Map.of(
                "disputeId", disputeId.toString(),
                "reason", request.reason(),
                "resolutionRequest", request.resolutionRequest() != null ? request.resolutionRequest() : "",
                "openedBy", callerEmail != null ? callerEmail : "unknown"
        ));

        return toDTO(entity);
    }

    @Transactional
    public DisputeDTO resolveDispute(UUID dealId, UUID disputeId, ResolveDisputeRequest request, String callerEmail) {
        dealPort.assertParty(dealId, callerEmail);

        DisputeEntity entity = disputeRepository.findByIdAndDealId(disputeId, dealId)
                .orElseThrow(() -> new SettlementNotFoundException("Dispute not found: " + disputeId));

        if (entity.getStatus() != DisputeStatus.OPEN && entity.getStatus() != DisputeStatus.UNDER_PARTIES_REVIEW) {
            throw new SettlementConflictException("Dispute " + disputeId + " is already resolved: " + entity.getStatus());
        }

        DisputeStatus resolution = request.resolution();
        if (resolution != DisputeStatus.RESOLVED_RELEASE_CAPTURE && resolution != DisputeStatus.RESOLVED_VOID_AUTHORIZATION) {
            throw new IllegalArgumentException("Resolution must be RESOLVED_RELEASE_CAPTURE or RESOLVED_VOID_AUTHORIZATION");
        }

        entity.setStatus(resolution);
        entity.setResolvedAt(Instant.now());
        disputeRepository.save(entity);

        // Transition deal state according to resolution
        if (resolution == DisputeStatus.RESOLVED_RELEASE_CAPTURE) {
            // Unfreezes capture: deal moves back to DELIVERABLES_SUBMITTED so capture can proceed
            dealPort.transitionDealStatus(dealId, DealStatus.DELIVERABLES_SUBMITTED);
            log.info("Dispute {} on deal {} resolved with RELEASE_CAPTURE. Deal transitioned to DELIVERABLES_SUBMITTED", disputeId, dealId);
        } else {
            // Voids authorization: deal transitions to CLOSED
            dealPort.transitionDealStatus(dealId, DealStatus.CLOSED);
            log.info("Dispute {} on deal {} resolved with VOID_AUTHORIZATION. Deal transitioned to CLOSED", disputeId, dealId);
        }

        auditEventPort.recordEvent(dealId, "DISPUTE_RESOLVED", Map.of(
                "disputeId", disputeId.toString(),
                "resolution", resolution.name(),
                "resolutionNotes", request.resolutionNotes() != null ? request.resolutionNotes() : "",
                "resolvedBy", callerEmail != null ? callerEmail : "unknown"
        ));

        return toDTO(entity);
    }

    public List<DisputeDTO> getDisputesForDeal(UUID dealId, String callerEmail) {
        dealPort.assertParty(dealId, callerEmail);
        return disputeRepository.findByDealIdOrderByOpenedAtDesc(dealId).stream()
                .map(this::toDTO)
                .toList();
    }

    private DisputeDTO toDTO(DisputeEntity entity) {
        return new DisputeDTO(
                entity.getId(),
                entity.getDealId(),
                entity.getStatus(),
                entity.getReason(),
                entity.getResolutionRequest(),
                entity.getOpenedAt(),
                entity.getResolvedAt()
        );
    }
}
