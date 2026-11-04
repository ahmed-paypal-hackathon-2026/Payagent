package com.payagent.ai.evaluation;

import com.payagent.ai.negotiation.NegotiationAction;
import com.payagent.ai.negotiation.NegotiationContext;
import com.payagent.ai.negotiation.NegotiationPolicyEngine;
import com.payagent.ai.negotiation.NegotiationStrategy;
import com.payagent.ai.negotiation.OfferConstraintValidator;
import com.payagent.ai.negotiation.PolicyDecision;
import com.payagent.common.contracts.OfferDTO;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic negotiation simulator used to compare strategies.
 *
 * <p>It drives the real {@link NegotiationPolicyEngine} for the agent side and a
 * simple, documented counterparty model for the other side. The counterparty
 * accepts when the agent's offer crosses its own hidden acceptance threshold and
 * otherwise concedes linearly toward its reservation price.</p>
 */
public final class NegotiationSimulator {

    private static final Instant SIMULATION_NOW = Instant.parse("2029-12-31T00:00:00Z");

    private final NegotiationPolicyEngine policyEngine = new NegotiationPolicyEngine();
    private final OfferConstraintValidator constraintValidator = new OfferConstraintValidator();

    public SimulationResult simulate(SyntheticScenario scenario, NegotiationStrategy strategy) {
        List<OfferDTO> offers = new ArrayList<>();
        List<NegotiationAction> actions = new ArrayList<>();
        if (scenario.counterpartyOpensFirst()) {
            offers.add(counterpartyOffer(scenario, 1, scenario.counterpartyOpening()));
        }

        boolean dealReached = false;
        long finalPrice = 0L;
        int rounds = 0;
        int violations = 0;
        int guard = scenario.maxRounds() + 8;

        while (guard-- > 0) {
            NegotiationContext context = scenario.context(offers);
            PolicyDecision decision = policyEngine.decide(context, strategy, SIMULATION_NOW);
            actions.add(decision.action());

            if (decision.action() == NegotiationAction.ACCEPT) {
                OfferDTO latest = context.latestCounterpartyOffer();
                if (latest == null) {
                    break;
                }
                finalPrice = latest.terms().priceMinorUnits();
                rounds = latest.roundNumber();
                dealReached = true;
                break;
            }
            if (decision.action() == NegotiationAction.STOP
                    || decision.action() == NegotiationAction.REJECT
                    || decision.action() == NegotiationAction.ESCALATE) {
                break;
            }
            if (decision.terms() == null) {
                break;
            }

            int agentRound = context.nextRoundNumber();
            StructuredTermsDTO agentTerms = decision.terms();
            violations += constraintValidator.inspect(
                    scenario.callerRole(), scenario.mandate(), agentTerms).size();
            offers.add(agentOffer(scenario, agentRound, agentTerms));

            if (counterpartyAccepts(scenario, agentTerms.priceMinorUnits())) {
                finalPrice = agentTerms.priceMinorUnits();
                rounds = agentRound;
                dealReached = true;
                break;
            }

            int counterpartyRound = agentRound + 1;
            long nextPrice = nextCounterpartyPrice(scenario, counterpartyRound);
            offers.add(counterpartyOffer(scenario, counterpartyRound, nextPrice));
            rounds = counterpartyRound;
        }

        double utility = ScenarioUtility.score(
                scenario, dealReached, finalPrice, Math.max(1, rounds));
        return new SimulationResult(
                scenario.scenarioId(),
                strategy,
                dealReached,
                finalPrice,
                rounds,
                violations,
                utility,
                actions);
    }

    private boolean counterpartyAccepts(SyntheticScenario scenario, long price) {
        if (scenario.callerRole() == PartyRole.BUYER) {
            long floor = scenario.counterpartyFloor();
            long target = scenario.counterpartyTarget();
            long threshold = floor + Math.round(
                    (1.0 - scenario.counterpartyAcceptanceShare()) * (target - floor));
            return price >= threshold;
        }
        long ceiling = scenario.counterpartyCeiling();
        long target = scenario.counterpartyTarget();
        long threshold = ceiling - Math.round(
                (1.0 - scenario.counterpartyAcceptanceShare()) * (ceiling - target));
        return price <= threshold;
    }

    private long nextCounterpartyPrice(SyntheticScenario scenario, int round) {
        int maxRounds = Math.max(2, scenario.maxRounds());
        double linear = Math.min(1.0, (double) (round - 1) / (maxRounds - 1));
        double progress = 1.0 - Math.exp(-scenario.counterpartyConcessionRate() * linear);
        double normalization = 1.0 - Math.exp(-scenario.counterpartyConcessionRate());
        if (normalization > 0.0) {
            progress /= normalization;
        }
        progress = Math.max(0.0, Math.min(1.0, progress));
        if (scenario.callerRole() == PartyRole.BUYER) {
            long floor = scenario.counterpartyFloor();
            long opening = scenario.counterpartyOpening();
            long price = opening + Math.round(progress * (floor - opening));
            return Math.max(floor, Math.min(opening, price));
        }
        long ceiling = scenario.counterpartyCeiling();
        long opening = scenario.counterpartyOpening();
        long price = opening + Math.round(progress * (ceiling - opening));
        return Math.min(ceiling, Math.max(opening, price));
    }

    private OfferDTO counterpartyOffer(SyntheticScenario scenario, int round, long price) {
        StructuredTermsDTO base = scenario.baseTerms();
        StructuredTermsDTO terms = new StructuredTermsDTO(
                base.title(),
                base.deliverables(),
                price,
                base.currency(),
                base.deliveryDays(),
                base.includedRevisions(),
                base.acceptanceCriteriaText());
        return new OfferDTO(
                scenario.scenarioId() + "-offer-" + round,
                scenario.mandate().dealId(),
                round,
                scenario.counterpartyRole(),
                false,
                terms,
                "Simulated counterparty offer.",
                "0",
                true,
                SIMULATION_NOW.plusSeconds(round * 60L).toString());
    }

    private OfferDTO agentOffer(
            SyntheticScenario scenario,
            int round,
            StructuredTermsDTO terms) {
        return new OfferDTO(
                scenario.scenarioId() + "-offer-" + round,
                scenario.mandate().dealId(),
                round,
                scenario.callerRole(),
                true,
                terms,
                "Simulated agent offer.",
                "0",
                true,
                SIMULATION_NOW.plusSeconds(round * 60L).toString());
    }
}
