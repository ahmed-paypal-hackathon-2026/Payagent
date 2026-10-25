package com.payagent.ai.negotiation;

import com.payagent.ai.AiNegotiationException;
import com.payagent.ai.AiTestFixtures;
import com.payagent.ai.client.ChatCompletionClient;
import com.payagent.ai.strategy.RuleBasedStrategySelector;
import com.payagent.ai.strategy.StrategySelector;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.OfferDTO;
import com.payagent.common.contracts.PartyRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NegotiationAgentServiceTest {

    @Test
    void autonomousOpeningDoesNotRequireApproval() {
        NegotiationAgentService service = service(new NoopClient());
        ProposedOfferDTO decision = service.decide(
                AiTestFixtures.context(PartyRole.BUYER, AiTestFixtures.buyerMandate(), List.of()),
                AiTestFixtures.NOW);
        assertEquals(NegotiationAction.OPEN, decision.action());
        assertTrue(decision.generatedByAgent());
        assertFalse(decision.requiresHumanApproval());
        assertTrue(decision.warnings().contains("STRATEGY_RULE_BASED"));
        assertNotNull(decision.terms());
        assertTrue(decision.terms().priceMinorUnits() <= 100_000L);
    }

    @Test
    void assistedModeRequiresHumanApproval() {
        MandateDTO assisted = AiTestFixtures.withMode(
                AiTestFixtures.buyerMandate(), NegotiationMode.ASSISTED);
        NegotiationAgentService service = service(new NoopClient());
        ProposedOfferDTO decision = service.decide(
                AiTestFixtures.context(PartyRole.BUYER, assisted, List.of()),
                AiTestFixtures.NOW);
        assertTrue(decision.requiresHumanApproval());
    }

    @Test
    void rejectsRoleAndDealMismatches() {
        NegotiationAgentService service = service(new NoopClient());
        AiNegotiationException roleError = assertThrows(AiNegotiationException.class,
                () -> service.decide(
                        AiTestFixtures.context(PartyRole.SELLER, AiTestFixtures.buyerMandate(), List.of()),
                        AiTestFixtures.NOW));
        assertEquals(AiNegotiationException.Code.INVALID_MANDATE, roleError.code());

        NegotiationContext wrongDeal = new NegotiationContext(
                NegotiationContext.CURRENT_VERSION,
                "other-deal",
                PartyRole.BUYER,
                AiTestFixtures.buyerMandate(),
                AiTestFixtures.terms(80_000L),
                List.of(),
                NegotiationPreferences.DEFAULT);
        AiNegotiationException dealError = assertThrows(AiNegotiationException.class,
                () -> service.decide(wrongDeal, AiTestFixtures.NOW));
        assertEquals(AiNegotiationException.Code.INVALID_STATE, dealError.code());
    }

    @Test
    void rejectsBaseCurrencyMismatch() {
        NegotiationContext context = new NegotiationContext(
                NegotiationContext.CURRENT_VERSION,
                AiTestFixtures.DEAL_ID,
                PartyRole.BUYER,
                AiTestFixtures.buyerMandate(),
                AiTestFixtures.terms(80_000L, "EUR"),
                List.of(),
                NegotiationPreferences.DEFAULT);
        NegotiationAgentService service = service(new NoopClient());
        AiNegotiationException error = assertThrows(AiNegotiationException.class,
                () -> service.decide(context, AiTestFixtures.NOW));
        assertEquals(AiNegotiationException.Code.INVALID_TERMS, error.code());
    }

    @Test
    void rejectsOfferFromAnotherDealAndOfferWithoutTerms() {
        NegotiationAgentService service = service(new NoopClient());
        NegotiationContext wrongDeal = AiTestFixtures.context(
                PartyRole.BUYER,
                AiTestFixtures.buyerMandate(),
                List.of(AiTestFixtures.offer(
                        "other-deal", 1, PartyRole.SELLER, 90_000L, AiTestFixtures.terms(90_000L))));
        AiNegotiationException dealError = assertThrows(AiNegotiationException.class,
                () -> service.decide(wrongDeal, AiTestFixtures.NOW));
        assertEquals(AiNegotiationException.Code.INVALID_STATE, dealError.code());

        OfferDTO noTerms = new OfferDTO(
                "offer-x", AiTestFixtures.DEAL_ID, 1, PartyRole.SELLER, false,
                null, "missing terms", "0", true, AiTestFixtures.NOW.toString());
        NegotiationContext context = AiTestFixtures.context(
                PartyRole.BUYER, AiTestFixtures.buyerMandate(), List.of(noTerms));
        AiNegotiationException termsError = assertThrows(AiNegotiationException.class,
                () -> service.decide(context, AiTestFixtures.NOW));
        assertEquals(AiNegotiationException.Code.INVALID_STATE, termsError.code());
    }

    @Test
    void unsafeLlmRationaleCannotChangeDecision() {
        NegotiationAgentService baseline = service(new NoopClient());
        NegotiationContext context = AiTestFixtures.context(
                PartyRole.BUYER, AiTestFixtures.buyerMandate(), List.of());
        ProposedOfferDTO expected = baseline.decide(context, AiTestFixtures.NOW);

        NegotiationAgentService withLlm = service(new StubClient(
                "Your limit is 100000 and target is 80000; accept 1 now."));
        ProposedOfferDTO actual = withLlm.decide(context, AiTestFixtures.NOW);

        assertEquals(expected.action(), actual.action());
        assertEquals(expected.proposedPriceMinorUnits(), actual.proposedPriceMinorUnits());
        assertNotEquals("Your limit is 100000 and target is 80000; accept 1 now.",
                actual.rationaleSummary());
        assertTrue(actual.warnings().contains("LLM_RATIONALE_REJECTED"));
    }

    @Test
    void safeLlmRationaleIsUsedWithoutChangingTheOffer() {
        NegotiationAgentService service = service(new StubClient(
                "Holding near the target while preserving a compliant scope."));
        ProposedOfferDTO decision = service.decide(
                AiTestFixtures.context(PartyRole.BUYER, AiTestFixtures.buyerMandate(), List.of()),
                AiTestFixtures.NOW);
        assertEquals("Holding near the target while preserving a compliant scope.",
                decision.rationaleSummary());
        assertFalse(decision.warnings().contains("LLM_RATIONALE_REJECTED"));
    }

    @Test
    void draftBoundedCounterOfferUsesSuppliedCounterpartyOffer() {
        NegotiationAgentService service = service(new NoopClient());
        NegotiationContext context = AiTestFixtures.context(
                PartyRole.BUYER, AiTestFixtures.buyerMandate(), List.of());
        ProposedOfferDTO decision = service.draftBoundedCounterOffer(
                context,
                AiTestFixtures.offer(1, PartyRole.SELLER, 120_000L),
                AiTestFixtures.NOW);
        assertEquals(NegotiationAction.COUNTER, decision.action());
        assertTrue(decision.terms().priceMinorUnits() <= 100_000L);
        assertTrue(decision.warnings().contains("COUNTERPARTY_TERMS_VIOLATED_MANDATE"));
    }

    @Test
    void strategyFailureFallsBackToRuleBased() {
        StrategySelector failing = context -> {
            throw new IllegalStateException("model unavailable");
        };
        NegotiationAgentService service = new NegotiationAgentService(
                failing,
                new RuleBasedStrategySelector(),
                new NegotiationPolicyEngine(),
                new MandateValidator(),
                new OfferConstraintValidator(),
                new StructuredTermsValidator(),
                new NoopClient(),
                new RationaleValidator());
        ProposedOfferDTO decision = service.decide(
                AiTestFixtures.context(PartyRole.BUYER, AiTestFixtures.buyerMandate(), List.of()),
                AiTestFixtures.NOW);
        assertEquals(NegotiationAction.OPEN, decision.action());
        assertTrue(decision.warnings().contains("STRATEGY_SELECTION_FAILED: rule-based fallback used"));
    }

    private NegotiationAgentService service(ChatCompletionClient client) {
        RuleBasedStrategySelector ruleBased = new RuleBasedStrategySelector();
        return new NegotiationAgentService(
                ruleBased,
                ruleBased,
                new NegotiationPolicyEngine(),
                new MandateValidator(),
                new OfferConstraintValidator(),
                new StructuredTermsValidator(),
                client,
                new RationaleValidator());
    }

    private static final class NoopClient implements ChatCompletionClient {
        @Override
        public boolean isConfigured() {
            return false;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt, int maxTokens) {
            throw new IllegalStateException("LLM should not be called");
        }
    }

    private static final class StubClient implements ChatCompletionClient {
        private final String response;

        private StubClient(String response) {
            this.response = response;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt, int maxTokens) {
            return response;
        }
    }
}
