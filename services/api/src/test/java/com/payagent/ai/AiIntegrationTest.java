package com.payagent.ai;

import com.payagent.ai.intake.IntakeExtractionResult;
import com.payagent.ai.intake.IntakeExtractionService;
import com.payagent.ai.negotiation.NegotiationAgentService;
import com.payagent.ai.negotiation.OfferConstraintValidator;
import com.payagent.ai.negotiation.ProposedOfferDTO;
import com.payagent.ai.strategy.HybridStrategySelector;
import com.payagent.ai.strategy.LearnedStrategySelector;
import com.payagent.ai.strategy.StrategySelection;
import com.payagent.common.contracts.PartyRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AiIntegrationTest {

    @Autowired
    private NegotiationAgentService agent;

    @Autowired
    private IntakeExtractionService intake;

    @Autowired
    private HybridStrategySelector selector;

    @Autowired
    private LearnedStrategySelector learned;

    @Autowired
    private OfferConstraintValidator constraints;

    @Test
    void committedModelLoadsAndPrimarySelectorUsesIt() {
        assertTrue(learned.isAvailable());
        StrategySelection selection = selector.select(
                AiTestFixtures.context(PartyRole.BUYER, AiTestFixtures.buyerMandate(), List.of()));
        assertEquals(StrategySelection.Source.LEARNED, selection.source());
        assertTrue(selection.confidence() >= 0.20);
    }

    @Test
    void agentProducesMandateCompliantOpeningInSpringContext() {
        ProposedOfferDTO decision = agent.decide(
                AiTestFixtures.context(PartyRole.BUYER, AiTestFixtures.buyerMandate(), List.of()),
                AiTestFixtures.NOW);
        assertNotNull(decision.terms());
        assertTrue(decision.proposedPriceMinorUnits() <= 100_000L);
        assertTrue(constraints.inspect(
                PartyRole.BUYER,
                AiTestFixtures.buyerMandate(),
                decision.terms(),
                AiTestFixtures.NOW).isEmpty());
        assertFalse(decision.toString().contains("limitPriceMinorUnits"));
        assertFalse(decision.toString().contains("mandate-buyer"));
    }

    @Test
    void intakeFallsBackWithoutAnLlmConfiguration() {
        String brief = """
                Build a landing page
                - Home page
                - Pricing page
                Budget: $1,000
                Delivery: 14 days
                Includes 2 revisions
                """;
        IntakeExtractionResult result = intake.extract(brief, "USD");
        assertEquals(IntakeExtractionResult.Source.DETERMINISTIC, result.source());
        assertTrue(result.warnings().contains("LLM_NOT_CONFIGURED"));
        assertEquals(100_000L, result.terms().priceMinorUnits());
        assertEquals(14, result.terms().deliveryDays());
        assertTrue(result.complete());
    }
}
