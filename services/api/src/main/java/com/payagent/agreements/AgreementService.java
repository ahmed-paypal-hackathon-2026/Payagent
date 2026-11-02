package com.payagent.agreements;

import com.payagent.common.contracts.AgreementSnapshotDTO;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.deals.DealService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AgreementService {

    private final AgreementRepository repository;
    private final DealService dealService;

    public AgreementService(AgreementRepository repository, DealService dealService) {
        this.repository = repository;
        this.dealService = dealService;
    }

    @Transactional
    public AgreementSnapshotDTO proposeAgreement(UUID dealId, StructuredTermsDTO terms) {
        dealService.getDealForUpdate(dealId);

        String canonicalJson = Rfc8785Canonicalizer.canonicalize(terms);
        byte[] canonicalBytes = Rfc8785Canonicalizer.canonicalBytes(terms);
        String hash = Sha256Hasher.hash(canonicalBytes);

        Integer nextVersion = repository.findTopByDealIdOrderByVersionDesc(dealId)
                .map(a -> a.getVersion() + 1)
                .orElse(1);

        repository.findTopByDealIdOrderByVersionDesc(dealId).ifPresent(old -> {
            old.clearAcceptances();
            repository.save(old);
        });

        Agreement agreement = new Agreement(UUID.randomUUID(), dealId, nextVersion, canonicalJson, hash);
        Agreement saved = repository.save(agreement);

        // Transition deal to TERMS_PENDING_ACCEPTANCE (canonical contract value; plan Section 2.2
        // refers to this as AGREEMENT_CANDIDATE — see ISSUE-004).
        dealService.setStatus(dealId, com.payagent.common.contracts.DealStatus.TERMS_PENDING_ACCEPTANCE);

        return toSnapshot(saved);
    }

    @Transactional
    public AgreementSnapshotDTO acceptAgreement(UUID dealId, int version, String termsHashSha256, String partyRole) {
        Agreement agreement = repository.findByDealIdAndVersionForUpdate(dealId, version)
                .orElseThrow(() -> new AgreementNotFoundException("Agreement not found"));

        com.payagent.deals.Deal deal = dealService.getDeal(dealId);
        if (deal.getStatus() != com.payagent.common.contracts.DealStatus.TERMS_PENDING_ACCEPTANCE) {
            throw new AgreementConflictException(
                    "Agreement acceptance not allowed in status " + deal.getStatus());
        }

        if (!agreement.getTermsHashSha256().equals(termsHashSha256)) {
            throw new AgreementConflictException("Hash mismatch");
        }

        // Acceptance by the correct party only.
        if (!"BUYER".equals(partyRole) && !"SELLER".equals(partyRole)) {
            throw new AgreementInvalidInputException("Invalid party role: " + partyRole);
        }

        // Stale version check: must be the latest version.
        Agreement latest = repository.findTopByDealIdOrderByVersionDesc(dealId)
                .orElseThrow(() -> new AgreementNotFoundException("No agreement"));
        if (!latest.getId().equals(agreement.getId())) {
            throw new AgreementConflictException("Stale version");
        }

        agreement.acceptBy(partyRole);
        Agreement saved = repository.save(agreement);

        // If both parties have accepted, transition deal to AGREED.
        if (saved.getBuyerAcceptedAt() != null && saved.getSellerAcceptedAt() != null) {
            dealService.setStatus(dealId, com.payagent.common.contracts.DealStatus.AGREED);
        }

        return toSnapshot(saved);
    }

    @Transactional
    public AgreementSnapshotDTO proposeNewVersion(UUID dealId, StructuredTermsDTO terms) {
        // Clearing prior acceptances is implicit: a new version row starts with null acceptances.
        // Explicitly clear on the latest old version for auditability.
        repository.findTopByDealIdOrderByVersionDesc(dealId).ifPresent(old -> {
            old.clearAcceptances();
            repository.save(old);
        });
        return proposeAgreement(dealId, terms);
    }

    private AgreementSnapshotDTO toSnapshot(Agreement a) {
        StructuredTermsDTO termsDto;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            termsDto = mapper.readValue(a.getCanonicalTermsJson(), StructuredTermsDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize canonical terms", e);
        }
        return new AgreementSnapshotDTO(
                a.getId().toString(),
                a.getDealId().toString(),
                a.getVersion(),
                termsDto,
                a.getTermsHashSha256(),
                a.getBuyerAcceptedAt() != null ? a.getBuyerAcceptedAt().toString() : null,
                a.getSellerAcceptedAt() != null ? a.getSellerAcceptedAt().toString() : null
        );
    }
}
