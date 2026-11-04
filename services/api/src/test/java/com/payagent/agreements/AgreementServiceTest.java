package com.payagent.agreements;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.common.contracts.AgreementSnapshotDTO;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.deals.Deal;
import com.payagent.deals.DealRepository;
import com.payagent.deals.DealService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AgreementServiceTest {

    @Autowired
    private AgreementService agreementService;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private AgreementRepository agreementRepository;

    @Autowired
    private DealService dealService;

    private Deal createDeal(DealStatus status) {
        Deal deal = new Deal(UUID.randomUUID(), UUID.randomUUID(), null, status);
        return dealRepository.save(deal);
    }

    @Test
    void proposeAgreement_transitionsToTermsPendingAcceptance() {
        Deal deal = createDeal(DealStatus.ACTIVE_NEGOTIATION);
        StructuredTermsDTO terms = new StructuredTermsDTO("T", List.of("a"), 1000L, "USD", 5, 0, "c");
        AgreementSnapshotDTO snapshot = agreementService.proposeAgreement(deal.getId(), terms);
        assertNotNull(snapshot);
        Deal persisted = dealRepository.findById(deal.getId()).orElseThrow();
        assertEquals(DealStatus.TERMS_PENDING_ACCEPTANCE, persisted.getStatus());
    }

    @Test
    void proposeAgreement_illegalTransition_throws() {
        Deal deal = createDeal(DealStatus.AGREED);
        StructuredTermsDTO terms = new StructuredTermsDTO("T", List.of("a"), 1000L, "USD", 5, 0, "c");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> agreementService.proposeAgreement(deal.getId(), terms));
        assertTrue(ex.getMessage().contains("Illegal deal transition"));
        Deal persisted = dealRepository.findById(deal.getId()).orElseThrow();
        assertEquals(DealStatus.AGREED, persisted.getStatus());
    }

    @Test
    void proposeAgreement_rejectedFromNonNegotiationStatuses() {
        StructuredTermsDTO terms = new StructuredTermsDTO("T", List.of("a"), 1000L, "USD", 5, 0, "c");
        List<DealStatus> blocked = List.of(
                DealStatus.DRAFT, DealStatus.INVITED, DealStatus.SETTLEMENT_PENDING,
                DealStatus.IN_PROGRESS, DealStatus.DELIVERABLES_SUBMITTED, DealStatus.ACCEPTED,
                DealStatus.DISPUTED, DealStatus.CLOSED, DealStatus.CANCELLED);
        for (DealStatus status : blocked) {
            Deal deal = createDeal(status);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> agreementService.proposeAgreement(deal.getId(), terms),
                    "Expected rejection from status " + status);
            assertTrue(ex.getMessage().contains("Illegal deal transition"), "Status " + status);
            assertEquals(status, dealRepository.findById(deal.getId()).orElseThrow().getStatus());
        }
    }

    @Test
    void acceptAgreement_bothParties_reachesAgreedViaValidTransition() throws Exception {
        Deal deal = createDeal(DealStatus.TERMS_PENDING_ACCEPTANCE);
        StructuredTermsDTO terms = new StructuredTermsDTO("T", List.of("a"), 1000L, "USD", 5, 0, "c");
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(terms);
        String hash = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
        Agreement agreement = new Agreement(UUID.randomUUID(), deal.getId(), 1, json, hash);
        agreementRepository.save(agreement);

        agreementService.acceptAgreement(deal.getId(), 1, hash, "BUYER");
        assertEquals(DealStatus.TERMS_PENDING_ACCEPTANCE, dealRepository.findById(deal.getId()).orElseThrow().getStatus());

        agreementService.acceptAgreement(deal.getId(), 1, hash, "SELLER");
        Deal persisted = dealRepository.findById(deal.getId()).orElseThrow();
        assertEquals(DealStatus.AGREED, persisted.getStatus());
    }

    @Test
    void proposeNewVersion_clearsAcceptances_andTransitions() {
        Deal deal = createDeal(DealStatus.TERMS_PENDING_ACCEPTANCE);
        StructuredTermsDTO terms1 = new StructuredTermsDTO("T1", List.of("a"), 1000L, "USD", 5, 0, "c");
        agreementService.proposeAgreement(deal.getId(), terms1);
        // simulate acceptance
        Agreement latest = agreementRepository.findTopByDealIdOrderByVersionDesc(deal.getId()).orElseThrow();
        latest.acceptBy("BUYER");
        latest.acceptBy("SELLER");
        agreementRepository.save(latest);

        StructuredTermsDTO terms2 = new StructuredTermsDTO("T2", List.of("b"), 2000L, "USD", 7, 1, "d");
        agreementService.proposeNewVersion(deal.getId(), terms2);
        Deal persisted = dealRepository.findById(deal.getId()).orElseThrow();
        assertEquals(DealStatus.TERMS_PENDING_ACCEPTANCE, persisted.getStatus());
        Agreement newVersion = agreementRepository.findTopByDealIdOrderByVersionDesc(deal.getId()).orElseThrow();
        assertNull(newVersion.getBuyerAcceptedAt());
        assertNull(newVersion.getSellerAcceptedAt());
    }

    @Test
    void acceptAgreement_requiresTermsPendingStatus() throws Exception {
        Deal deal = createDeal(DealStatus.ACTIVE_NEGOTIATION);
        ObjectMapper mapper = new ObjectMapper();
        StructuredTermsDTO terms = new StructuredTermsDTO("T", List.of("a"), 1000L, "USD", 5, 0, "c");
        String hash = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
        agreementRepository.save(new Agreement(UUID.randomUUID(), deal.getId(), 1, mapper.writeValueAsString(terms), hash));

        AgreementConflictException ex = assertThrows(AgreementConflictException.class,
                () -> agreementService.acceptAgreement(deal.getId(), 1, hash, "BUYER"));
        assertTrue(ex.getMessage().contains("not allowed"));
    }

    @Test
    void acceptAgreement_rejectedAfterDealAgreed() {
        Deal deal = createDeal(DealStatus.ACTIVE_NEGOTIATION);
        StructuredTermsDTO terms = new StructuredTermsDTO("T", List.of("a"), 1000L, "USD", 5, 0, "c");
        AgreementSnapshotDTO v1 = agreementService.proposeAgreement(deal.getId(), terms);
        String hash = v1.termsHashSha256();
        agreementService.acceptAgreement(deal.getId(), v1.version(), hash, "BUYER");
        agreementService.acceptAgreement(deal.getId(), v1.version(), hash, "SELLER");
        assertEquals(DealStatus.AGREED, dealRepository.findById(deal.getId()).orElseThrow().getStatus());

        AgreementConflictException ex = assertThrows(AgreementConflictException.class,
                () -> agreementService.acceptAgreement(deal.getId(), v1.version(), hash, "BUYER"));
        assertTrue(ex.getMessage().contains("not allowed"));
    }

    @Test
    void proposeAgreement_withOneCentChange_clearsBuyerAcceptance() {
        Deal deal = createDeal(DealStatus.ACTIVE_NEGOTIATION);
        StructuredTermsDTO terms1 = new StructuredTermsDTO("T", List.of("a"), 1000L, "USD", 5, 0, "c");
        AgreementSnapshotDTO v1 = agreementService.proposeAgreement(deal.getId(), terms1);
        String hash1 = v1.termsHashSha256();
        agreementService.acceptAgreement(deal.getId(), v1.version(), hash1, "BUYER");

        StructuredTermsDTO terms2 = new StructuredTermsDTO("T", List.of("a"), 1001L, "USD", 5, 0, "c");
        AgreementSnapshotDTO v2 = agreementService.proposeAgreement(deal.getId(), terms2);

        Agreement previous = agreementRepository.findByDealIdAndVersion(deal.getId(), v1.version()).orElseThrow();
        Agreement latest = agreementRepository.findTopByDealIdOrderByVersionDesc(deal.getId()).orElseThrow();
        assertNull(previous.getBuyerAcceptedAt());
        assertEquals(v2.version(), latest.getVersion());
        assertNotEquals(hash1, v2.termsHashSha256());
    }

    @Test
    void proposeAgreement_withOneRevisionChange_clearsBuyerAcceptance() {
        Deal deal = createDeal(DealStatus.ACTIVE_NEGOTIATION);
        StructuredTermsDTO terms1 = new StructuredTermsDTO("T", List.of("a"), 1000L, "USD", 5, 0, "c");
        AgreementSnapshotDTO v1 = agreementService.proposeAgreement(deal.getId(), terms1);
        String hash1 = v1.termsHashSha256();
        agreementService.acceptAgreement(deal.getId(), v1.version(), hash1, "BUYER");

        StructuredTermsDTO terms2 = new StructuredTermsDTO("T", List.of("a"), 1000L, "USD", 5, 1, "c");
        AgreementSnapshotDTO v2 = agreementService.proposeAgreement(deal.getId(), terms2);

        Agreement previous = agreementRepository.findByDealIdAndVersion(deal.getId(), v1.version()).orElseThrow();
        Agreement latest = agreementRepository.findTopByDealIdOrderByVersionDesc(deal.getId()).orElseThrow();
        assertNull(previous.getBuyerAcceptedAt());
        assertEquals(v2.version(), latest.getVersion());
        assertNotEquals(hash1, v2.termsHashSha256());
    }
}
