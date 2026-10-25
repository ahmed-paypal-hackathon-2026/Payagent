package com.payagent.ai.evaluation;

import com.payagent.ai.AiTestFixtures;
import com.payagent.ai.negotiation.MandateValidator;
import com.payagent.ai.negotiation.NegotiationStrategy;
import com.payagent.common.contracts.PartyRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticEvaluationTest {

    private final SyntheticScenarioGenerator generator = new SyntheticScenarioGenerator();
    private final NegotiationSimulator simulator = new NegotiationSimulator();
    private final MandateValidator mandateValidator = new MandateValidator();

    @Test
    void generationIsDeterministicAndCoversBothRoles() {
        List<SyntheticScenario> first = generator.generate(200, 4242L);
        List<SyntheticScenario> second = generator.generate(200, 4242L);
        assertEquals(first, second);
        assertTrue(first.stream().anyMatch(s -> s.callerRole() == PartyRole.BUYER));
        assertTrue(first.stream().anyMatch(s -> s.callerRole() == PartyRole.SELLER));
        for (SyntheticScenario scenario : first) {
            assertTrue(scenario.mandate().limitPriceMinorUnits() > 0);
            assertTrue(scenario.mandate().targetPriceMinorUnits() > 0);
            mandateValidator.validate(scenario.mandate(), AiTestFixtures.NOW);
        }
    }

    @Test
    void simulationIsDeterministicAndNeverEmitsNonCompliantAgentTerms() {
        List<SyntheticScenario> scenarios = generator.generate(40, 99L);
        for (SyntheticScenario scenario : scenarios) {
            for (NegotiationStrategy strategy : NegotiationStrategy.values()) {
                SimulationResult first = simulator.simulate(scenario, strategy);
                SimulationResult second = simulator.simulate(scenario, strategy);
                assertEquals(first, second);
                assertEquals(0, first.constraintViolations(),
                        scenario.scenarioId() + " " + strategy);
                assertTrue(first.utility() >= 0.0 && first.utility() <= 1.0);
                if (!first.dealReached()) {
                    assertEquals(0.0, first.utility(), 1e-12);
                }
            }
        }
    }

    @Test
    void utilityReflectsPricePreferenceAndNoDeal() {
        SyntheticScenario buyer = generator.generate(1, 7L).getFirst();
        assertEquals(0.0, ScenarioUtility.score(buyer, false, 0L, 1), 1e-12);
        assertTrue(ScenarioUtility.score(buyer, true, buyer.mandate().targetPriceMinorUnits(), 2)
                >= ScenarioUtility.score(buyer, true, buyer.mandate().limitPriceMinorUnits(), 2));
        assertFalse(buyer.scenarioId().isBlank());
    }
}
