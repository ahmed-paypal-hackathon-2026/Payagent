package com.payagent.deals;

import com.payagent.agreements.AgreementRepository;
import com.payagent.agreements.AgreementService;
import com.payagent.ai.negotiation.NegotiationAction;
import com.payagent.ai.negotiation.ProposedOfferDTO;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.mandates.MandateRepository;
import com.payagent.mandates.MandateService;
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
class NegotiationAgreementBridgeIntegrationTest {

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private DealService dealService;

    @Autowired
    private AgreementRepository agreementRepository;

    @Autowired
    private AgreementService agreementService;

    @Autowired
    private MandateRepository mandateRepository;

    @Autowired
    private MandateService mandateService;

    @Autowired
    private NegotiationAgreementBridge bridge;

    @Test
    void eligibleAcceptCreatesAgreementAndTransitions() {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        Deal deal = dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));

        // create mandates
        mandateRepository.save(createMandateEntity(dealId, PartyRole.BUYER));
        mandateRepository.save(createMandateEntity(dealId, PartyRole.SELLER));

        StructuredTermsDTO terms = new StructuredTermsDTO("t", List.of("core"), 90000L, "USD", 15, 4, "ok");
        ProposedOfferDTO decision = buildDecision(dealId.toString(), PartyRole.BUYER, NegotiationAction.ACCEPT, terms, false);

        var snapshot = bridge.proposeFromNegotiation(dealId, buyer, decision);
        assertNotNull(snapshot);
        assertEquals(1, snapshot.version());

        Deal updated = dealRepository.findById(dealId).orElseThrow();
        assertEquals(DealStatus.TERMS_PENDING_ACCEPTANCE, updated.getStatus());
    }

    @Test
    void nonAcceptActionDoesNotCreateAgreement() {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        Deal deal = dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(createMandateEntity(dealId, PartyRole.BUYER));
        mandateRepository.save(createMandateEntity(dealId, PartyRole.SELLER));

        StructuredTermsDTO terms = new StructuredTermsDTO("t", List.of("core"), 90000L, "USD", 15, 4, "ok");
        ProposedOfferDTO decision = buildDecision(dealId.toString(), PartyRole.BUYER, NegotiationAction.COUNTER, terms, false);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> bridge.proposeFromNegotiation(dealId, buyer, decision));
        assertTrue(ex.getMessage().contains("not eligible"));
        assertTrue(agreementRepository.findAll().isEmpty());
    }

    @Test
    void nonParticipantRejected() {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        Deal deal = dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.ACTIVE_NEGOTIATION));
        mandateRepository.save(createMandateEntity(dealId, PartyRole.BUYER));
        mandateRepository.save(createMandateEntity(dealId, PartyRole.SELLER));

        StructuredTermsDTO terms = new StructuredTermsDTO("t", List.of(), 90000L, "USD", 15, 4, "ok");
        ProposedOfferDTO decision = buildDecision(dealId.toString(), PartyRole.BUYER, NegotiationAction.ACCEPT, terms, false);

        assertThrows(SecurityException.class, () -> bridge.proposeFromNegotiation(dealId, outsider, decision));
    }

    @Test
    void staleHashRejectedOnAccept() {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        dealRepository.save(new Deal(dealId, buyer, seller, DealStatus.TERMS_PENDING_ACCEPTANCE));

        StructuredTermsDTO termsV1 = new StructuredTermsDTO("t", List.of(), 90000L, "USD", 15, 4, "ok");
        var snap1 = agreementService.proposeAgreement(dealId, termsV1);
        assertEquals(1, snap1.version());

        StructuredTermsDTO termsV2 = new StructuredTermsDTO("t", List.of(), 95000L, "USD", 15, 4, "ok");
        var snap2 = agreementService.proposeNewVersion(dealId, termsV2);
        assertEquals(2, snap2.version());

        // Accept old version
        com.payagent.agreements.AgreementConflictException ex = assertThrows(com.payagent.agreements.AgreementConflictException.class,
                () -> agreementService.acceptAgreement(dealId, 1, snap1.termsHashSha256(), "BUYER"));
        assertEquals("Stale version", ex.getMessage());
    }

    private com.payagent.mandates.Mandate createMandateEntity(UUID dealId, PartyRole role) {
        return new com.payagent.mandates.Mandate(
                UUID.randomUUID(),
                dealId,
                role,
                1,
                "USD",
                120000L,
                90000L,
                20,
                15,
                2,
                4,
                "[]",
                "[]",
                NegotiationMode.ASSISTED,
                10,
                java.time.OffsetDateTime.now().plusYears(1)
        );
    }

    private ProposedOfferDTO buildDecision(String dealId, PartyRole role, NegotiationAction action, StructuredTermsDTO terms, boolean requiresHumanApproval) {
        return new ProposedOfferDTO(
                dealId,
                role,
                action,
                com.payagent.ai.negotiation.NegotiationStrategy.BALANCED,
                com.payagent.ai.strategy.StrategySelection.Source.DEFAULT,
                terms,
                terms.priceMinorUnits(),
                "test",
                0.9,
                true,
                false,
                requiresHumanApproval,
                List.of(),
                "test"
        );
    }
}
