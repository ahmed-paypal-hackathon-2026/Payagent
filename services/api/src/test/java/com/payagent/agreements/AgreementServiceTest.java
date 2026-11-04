package com.payagent.agreements;

import com.payagent.auth.User;
import com.payagent.auth.UserRepository;
import com.payagent.common.contracts.AgreementSnapshotDTO;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.deals.Deal;
import com.payagent.deals.DealService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AgreementServiceTest {

    @Autowired private AgreementService agreementService;
    @Autowired private DealService dealService;
    @Autowired private UserRepository userRepository;

    private static final List<String> DELIVERED = List.of("figma", "source", "notes");

    private User makeUser(String email) {
        return userRepository.save(new User(UUID.randomUUID(), email, "hash", OffsetDateTime.now()));
    }

    private Deal makeDeal() {
        User b = makeUser("ab@test.com");
        User s = makeUser("as@test.com");
        return dealService.createDeal(b.getId(), s.getId());
    }

    private StructuredTermsDTO terms(long price, int delivery, int revisions) {
        return new StructuredTermsDTO("Design sprint", DELIVERED, price, "USD", delivery, revisions, "Lighthouse > 90");
    }

    @Test
    void proposeAgreement_createsVersionOneWithHash() {
        Deal deal = makeDeal();
        AgreementSnapshotDTO snap = agreementService.proposeAgreement(deal.getId(), terms(80000, 10, 3));
        assertEquals(1, snap.version());
        assertNotNull(snap.termsHashSha256());
        assertEquals(64, snap.termsHashSha256().length());
        assertNull(snap.buyerAcceptedAt());
        assertNull(snap.sellerAcceptedAt());
    }

    @Test
    void acceptAgreement_buyerThenSeller_transitionsDealToAgreed() {
        Deal deal = makeDeal();
        AgreementSnapshotDTO snap = agreementService.proposeAgreement(deal.getId(), terms(80000, 10, 3));

        AgreementSnapshotDTO afterBuyer = agreementService.acceptAgreement(
                deal.getId(), snap.version(), snap.termsHashSha256(), "BUYER");
        assertNotNull(afterBuyer.buyerAcceptedAt());
        assertNull(afterBuyer.sellerAcceptedAt());

        AgreementSnapshotDTO afterSeller = agreementService.acceptAgreement(
                deal.getId(), snap.version(), snap.termsHashSha256(), "SELLER");
        assertNotNull(afterSeller.buyerAcceptedAt());
        assertNotNull(afterSeller.sellerAcceptedAt());

        assertEquals(DealStatus.AGREED, dealService.getDeal(deal.getId()).getStatus());
    }

    @Test
    void acceptAgreement_singleAcceptance_doesNotTransitionToAgreed() {
        Deal deal = makeDeal();
        AgreementSnapshotDTO snap = agreementService.proposeAgreement(deal.getId(), terms(80000, 10, 3));

        // Only buyer accepts — deal must NOT transition to AGREED
        agreementService.acceptAgreement(
                deal.getId(), snap.version(), snap.termsHashSha256(), "BUYER");

        assertNotEquals(DealStatus.AGREED, dealService.getDeal(deal.getId()).getStatus());
        assertEquals(DealStatus.TERMS_PENDING_ACCEPTANCE,
                dealService.getDeal(deal.getId()).getStatus());
    }

    @Test
    void acceptAgreement_staleHash_throws() {
        Deal deal = makeDeal();
        AgreementSnapshotDTO snap = agreementService.proposeAgreement(deal.getId(), terms(80000, 10, 3));
        assertThrows(IllegalArgumentException.class,
                () -> agreementService.acceptAgreement(deal.getId(), snap.version(), "0".repeat(64), "BUYER"));
    }

    @Test
    void acceptAgreement_staleVersion_throws() {
        Deal deal = makeDeal();
        AgreementSnapshotDTO snap = agreementService.proposeAgreement(deal.getId(), terms(80000, 10, 3));
        assertThrows(IllegalArgumentException.class,
                () -> agreementService.acceptAgreement(deal.getId(), snap.version() + 1, snap.termsHashSha256(), "BUYER"));
    }

    @Test
    void acceptAgreement_invalidPartyRole_throws() {
        Deal deal = makeDeal();
        AgreementSnapshotDTO snap = agreementService.proposeAgreement(deal.getId(), terms(80000, 10, 3));
        assertThrows(IllegalArgumentException.class,
                () -> agreementService.acceptAgreement(deal.getId(), snap.version(), snap.termsHashSha256(), "ADMIN"));
    }

    @Test
    void proposeNewVersion_incrementsVersionAndClearsAcceptances() {
        Deal deal = makeDeal();
        AgreementSnapshotDTO v1 = agreementService.proposeAgreement(deal.getId(), terms(80000, 10, 3));
        agreementService.acceptAgreement(deal.getId(), v1.version(), v1.termsHashSha256(), "BUYER");

        AgreementSnapshotDTO v2 = agreementService.proposeNewVersion(deal.getId(), terms(85000, 12, 2));
        assertEquals(2, v2.version());
        assertNull(v2.buyerAcceptedAt());
        assertNull(v2.sellerAcceptedAt());

        // v1 acceptances cleared (new version row starts with null acceptances)
        assertNull(v2.buyerAcceptedAt());
        assertNull(v2.sellerAcceptedAt());

        // Re-accepting the stale (non-latest) version is rejected.
        assertThrows(IllegalArgumentException.class,
                () -> agreementService.acceptAgreement(deal.getId(), v1.version(), v1.termsHashSha256(), "BUYER"));
    }

    @Test
    void proposeAgreement_hashIsDeterministic() {
        Deal deal = makeDeal();
        AgreementSnapshotDTO a = agreementService.proposeAgreement(deal.getId(), terms(80000, 10, 3));
        // propose again creates new version with same hash
        AgreementSnapshotDTO b = agreementService.proposeAgreement(deal.getId(), terms(80000, 10, 3));
        assertEquals(a.termsHashSha256(), b.termsHashSha256());
    }

    @Test
    void proposeAgreement_changedTermsProducesNewHash() {
        Deal deal = makeDeal();
        AgreementSnapshotDTO a = agreementService.proposeAgreement(deal.getId(), terms(80000, 10, 3));
        AgreementSnapshotDTO b = agreementService.proposeAgreement(deal.getId(), terms(85000, 12, 2));
        assertNotEquals(a.termsHashSha256(), b.termsHashSha256());
    }
}
