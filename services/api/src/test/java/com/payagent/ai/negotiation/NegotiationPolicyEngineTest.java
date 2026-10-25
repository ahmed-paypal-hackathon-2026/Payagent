package com.payagent.ai.negotiation;

import com.payagent.ai.AiNegotiationException;
import com.payagent.ai.AiTestFixtures;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.PartyRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NegotiationPolicyEngineTest {

    private final NegotiationPolicyEngine policy = new NegotiationPolicyEngine();
    private final OfferConstraintValidator constraints = new OfferConstraintValidator();

    @Test
    void opensInsideTheBuyerMandate() {
        PolicyDecision decision = policy.decide(
                AiTestFixtures.context(PartyRole.BUYER, AiTestFixtures.buyerMandate(), List.of()),
                NegotiationStrategy.BALANCED,
                AiTestFixtures.NOW);
        assertEquals(NegotiationAction.OPEN, decision.action());
        assertEquals("OPENING_OFFER", decision.reasonCode());
        assertNotNull(decision.terms());
        assertTrue(decision.terms().priceMinorUnits() <= 100_000L);
        assertTrue(decision.terms().priceMinorUnits() >= 0L);
        assertTrue(constraints.inspect(
                PartyRole.BUYER, AiTestFixtures.buyerMandate(),
                decision.terms(), AiTestFixtures.NOW).isEmpty());
    }

    @Test
    void opensInsideTheSellerMandate() {
        PolicyDecision decision = policy.decide(
                AiTestFixtures.context(PartyRole.SELLER, AiTestFixtures.sellerMandate(), List.of()),
                NegotiationStrategy.FIRM,
                AiTestFixtures.NOW);
        assertEquals(NegotiationAction.OPEN, decision.action());
        assertTrue(decision.terms().priceMinorUnits() >= 70_000L);
        assertTrue(decision.terms().priceMinorUnits() <= 110_000L);
        assertTrue(constraints.inspect(
                PartyRole.SELLER, AiTestFixtures.sellerMandate(),
                decision.terms(), AiTestFixtures.NOW).isEmpty());
    }

    @Test
    void acceptsCompliantBuyerFriendlyOffer() {
        PolicyDecision decision = policy.decide(
                AiTestFixtures.context(PartyRole.BUYER, AiTestFixtures.buyerMandate(),
                        List.of(AiTestFixtures.offer(1, PartyRole.SELLER, 75_000L))),
                NegotiationStrategy.BALANCED,
                AiTestFixtures.NOW);
        assertEquals(NegotiationAction.ACCEPT, decision.action());
        assertEquals(75_000L, decision.terms().priceMinorUnits());
        assertEquals("ACCEPT_WITHIN_POLICY", decision.reasonCode());
    }

    @Test
    void acceptsCompliantSellerFriendlyOffer() {
        PolicyDecision decision = policy.decide(
                AiTestFixtures.context(PartyRole.SELLER, AiTestFixtures.sellerMandate(),
                        List.of(AiTestFixtures.offer(1, PartyRole.BUYER, 95_000L))),
                NegotiationStrategy.BALANCED,
                AiTestFixtures.NOW);
        assertEquals(NegotiationAction.ACCEPT, decision.action());
        assertEquals(95_000L, decision.terms().priceMinorUnits());
    }

    @Test
    void countersUnsafeBuyerOfferWithoutCrossingTheCeiling() {
        PolicyDecision decision = policy.decide(
                AiTestFixtures.context(PartyRole.BUYER, AiTestFixtures.buyerMandate(),
                        List.of(AiTestFixtures.offer(1, PartyRole.SELLER, 120_000L))),
                NegotiationStrategy.BALANCED,
                AiTestFixtures.NOW);
        assertEquals(NegotiationAction.COUNTER, decision.action());
        assertTrue(decision.terms().priceMinorUnits() <= 100_000L);
        assertTrue(decision.warnings().contains("COUNTERPARTY_TERMS_VIOLATED_MANDATE"));
        assertTrue(constraints.inspect(
                PartyRole.BUYER, AiTestFixtures.buyerMandate(),
                decision.terms(), AiTestFixtures.NOW).isEmpty());
    }

    @Test
    void countersUnsafeSellerOfferWithoutGoingBelowFloor() {
        PolicyDecision decision = policy.decide(
                AiTestFixtures.context(PartyRole.SELLER, AiTestFixtures.sellerMandate(),
                        List.of(AiTestFixtures.offer(1, PartyRole.BUYER, 50_000L))),
                NegotiationStrategy.PATIENT_PRICE,
                AiTestFixtures.NOW);
        assertEquals(NegotiationAction.COUNTER, decision.action());
        assertTrue(decision.terms().priceMinorUnits() >= 70_000L);
        assertTrue(constraints.inspect(
                PartyRole.SELLER, AiTestFixtures.sellerMandate(),
                decision.terms(), AiTestFixtures.NOW).isEmpty());
    }

    @Test
    void stopsOnFinalRoundInsteadOfAcceptingAnUnacceptableOffer() {
        MandateDTO oneRound = new MandateDTO(
                "m", AiTestFixtures.DEAL_ID, PartyRole.BUYER, 1, "USD",
                100_000L, 80_000L, 30, 21, 2, 2,
                List.of("source code"), List.of("crypto payment"),
                com.payagent.common.contracts.NegotiationMode.AUTONOMOUS_BOUNDED,
                1, AiTestFixtures.FUTURE);
        PolicyDecision decision = policy.decide(
                AiTestFixtures.context(PartyRole.BUYER, oneRound,
                        List.of(AiTestFixtures.offer(1, PartyRole.SELLER, 95_000L))),
                NegotiationStrategy.BALANCED,
                AiTestFixtures.NOW);
        assertEquals(NegotiationAction.STOP, decision.action());
        assertEquals("MAX_ROUNDS_REACHED", decision.reasonCode());
        assertFalse(decision.fallbackUsed());
    }

    @Test
    void stopsOnFinalRoundWhenLastOfferViolatesMandate() {
        MandateDTO oneRound = new MandateDTO(
                "m", AiTestFixtures.DEAL_ID, PartyRole.BUYER, 1, "USD",
                100_000L, 80_000L, 30, 21, 2, 2,
                List.of("source code"), List.of("crypto payment"),
                com.payagent.common.contracts.NegotiationMode.AUTONOMOUS_BOUNDED,
                1, AiTestFixtures.FUTURE);
        PolicyDecision decision = policy.decide(
                AiTestFixtures.context(PartyRole.BUYER, oneRound,
                        List.of(AiTestFixtures.offer(1, PartyRole.SELLER, 150_000L))),
                NegotiationStrategy.BALANCED,
                AiTestFixtures.NOW);
        assertEquals(NegotiationAction.STOP, decision.action());
        assertEquals("POLICY_VIOLATION_BLOCKED", decision.reasonCode());
        assertTrue(decision.warnings().contains("LAST_OFFER_OUTSIDE_MANDATE"));
    }

    @Test
    void rejectsExpiredMandateBeforeProducingTerms() {
        MandateDTO expired = AiTestFixtures.withExpiry(
                AiTestFixtures.buyerMandate(), "2029-12-30T00:00:00Z");
        AiNegotiationException error = assertThrows(AiNegotiationException.class,
                () -> policy.decide(
                        AiTestFixtures.context(PartyRole.BUYER, expired, List.of()),
                        NegotiationStrategy.BALANCED,
                        AiTestFixtures.NOW));
        assertEquals(AiNegotiationException.Code.MANDATE_EXPIRED, error.code());
    }

    @Test
    void rebuildsSafeCounterWhenCounterpartyScopeViolatesRequiredTerms() {
        var badScope = AiTestFixtures.terms(
                90_000L, 21, 2, "Website with crypto payment",
                List.of("documentation"));
        PolicyDecision decision = policy.decide(
                AiTestFixtures.context(PartyRole.BUYER, AiTestFixtures.buyerMandate(),
                        List.of(AiTestFixtures.offer(
                                AiTestFixtures.DEAL_ID, 1, PartyRole.SELLER, 90_000L, badScope))),
                NegotiationStrategy.BALANCED,
                AiTestFixtures.NOW);
        assertEquals(NegotiationAction.COUNTER, decision.action());
        assertTrue(decision.warnings().contains("COUNTERPARTY_TERMS_VIOLATED_MANDATE"));
        assertTrue(constraints.inspect(
                PartyRole.BUYER, AiTestFixtures.buyerMandate(),
                decision.terms(), AiTestFixtures.NOW).isEmpty());
    }
}
