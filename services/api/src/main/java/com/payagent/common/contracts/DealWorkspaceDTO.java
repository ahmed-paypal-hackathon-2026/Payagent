package com.payagent.common.contracts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DealWorkspaceDTO(
        @NotBlank String dealId,
        @NotNull DealStatus status,
        @NotNull NegotiationStopReason stopReason,
        @NotBlank String buyerEmail,
        String sellerEmail,
        @NotNull PartyRole myRole,
        MandateDTO myMandate,
        @NotNull List<OfferDTO> offers,
        AgreementSnapshotDTO currentAgreement,
        @NotNull SettlementStateDTO settlement,
        @NotNull List<DeliverableEvidenceDTO> evidenceList,
        @NotNull DisputeStatus disputeStatus,
        String disputeReason) {}
