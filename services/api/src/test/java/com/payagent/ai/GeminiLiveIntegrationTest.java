package com.payagent.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.ai.client.ChatCompletionClient;
import com.payagent.ai.client.LlmCallException;
import com.payagent.ai.client.OpenAiCompatibleChatClient;
import com.payagent.ai.intake.DeterministicIntakeExtractor;
import com.payagent.ai.intake.IntakeExtractionResult;
import com.payagent.ai.intake.IntakeExtractionService;
import com.payagent.ai.negotiation.MandateValidator;
import com.payagent.ai.negotiation.NegotiationAction;
import com.payagent.ai.negotiation.NegotiationAgentService;
import com.payagent.ai.negotiation.NegotiationContext;
import com.payagent.ai.negotiation.NegotiationPolicyEngine;
import com.payagent.ai.negotiation.NegotiationPreferences;
import com.payagent.ai.negotiation.OfferConstraintValidator;
import com.payagent.ai.negotiation.PromptSanitizer;
import com.payagent.ai.negotiation.ProposedOfferDTO;
import com.payagent.ai.negotiation.RationaleValidator;
import com.payagent.ai.negotiation.StructuredTermsValidator;
import com.payagent.ai.strategy.RuleBasedStrategySelector;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.OfferDTO;
import com.payagent.common.contracts.PartyRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.opentest4j.TestAbortedException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in live test for an OpenAI-compatible endpoint (Gemini by default).
 *
 * <p>It is skipped unless {@code PAYAGENT_LIVE_LLM=true}. The API key is read
 * from the environment and is never stored in the repository. Run it with the
 * local {@code .env} loaded:</p>
 *
 * <pre>
 * set -a; . ./.env; set +a
 * PAYAGENT_LIVE_LLM=true ./mvnw -B -Dtest=GeminiLiveIntegrationTest test
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "PAYAGENT_LIVE_LLM", matches = "(?i)true")
class GeminiLiveIntegrationTest {

    @Test
    void chatCompletionReturnsContent() {
        String content;
        try {
            content = resilientClient().complete(
                    "You are a connectivity test.",
                    "Reply with exactly: OK",
                    512);
        } catch (LlmCallException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("HTTP 429")) {
                throw new TestAbortedException(
                        "Gemini live test skipped: rate or daily quota limit reached");
            }
            throw ex;
        }
        assertFalse(content.isBlank());
        assertTrue(content.toUpperCase(Locale.ROOT).contains("OK"));
    }

    @Test
    void intakeExtractionUsesTheConfiguredLiveModel() {
        IntakeExtractionService service = new IntakeExtractionService(
                resilientClient(),
                new PromptSanitizer(),
                new StructuredTermsValidator(),
                new DeterministicIntakeExtractor(),
                new ObjectMapper());
        String brief = """
                Build a landing page
                - Home page with hero section
                - Pricing page
                Budget: $1,000
                Delivery: 14 days
                Includes 2 revisions
                Acceptance: all links work.
                """;
        IntakeExtractionResult result = service.extract(brief, "USD");
        abortIfUnavailable(result);
        assertEquals(IntakeExtractionResult.Source.LLM, result.source());
        assertFalse(result.warnings().contains("LLM_UNAVAILABLE"));
        assertFalse(result.warnings().contains("LLM_RESPONSE_INVALID"));
        assertEquals(100_000L, result.terms().priceMinorUnits());
        assertEquals(14, result.terms().deliveryDays());
        assertTrue(result.complete());
    }

    @Test
    void negotiationAgentUsesTheLiveRationaleWithoutChangingTheDecision() {
        ChatCompletionClient client = resilientClient();
        NegotiationAgentService service = new NegotiationAgentService(
                new RuleBasedStrategySelector(),
                new RuleBasedStrategySelector(),
                new NegotiationPolicyEngine(),
                new MandateValidator(),
                new OfferConstraintValidator(),
                new StructuredTermsValidator(),
                client,
                new RationaleValidator(),
                512);
        ProposedOfferDTO decision = service.decide(
                AiTestFixtures.context(
                        PartyRole.BUYER, AiTestFixtures.buyerMandate(), java.util.List.of()),
                AiTestFixtures.NOW);
        abortIfRationaleUnavailable(decision);
        assertNotNull(decision.terms());
        assertFalse(decision.rationaleSummary().isBlank());
        assertFalse(decision.warnings().contains("LLM_RATIONALE_UNAVAILABLE"));
        assertFalse(decision.warnings().contains("LLM_RATIONALE_INVALID"));
        assertFalse(decision.warnings().contains("LLM_RATIONALE_REJECTED"));
    }

    @Test
    void multiTurnNegotiationChatReachesAMandateSafeAgreement() {
        NegotiationAgentService buyer = agent(resilientClient());
        NegotiationAgentService seller = agent(resilientClient());
        MandateDTO buyerMandate = AiTestFixtures.buyerMandate();
        MandateDTO sellerMandate = AiTestFixtures.sellerMandate();
        NegotiationPreferences buyerPreferences = NegotiationPreferences.of(0.2, 0.2, 0.2, 0.8);
        NegotiationPreferences sellerPreferences = NegotiationPreferences.of(0.8, 0.2, 0.8, 0.2);
        List<OfferDTO> history = new ArrayList<>();
        ProposedOfferDTO decision = null;
        PartyRole turn = PartyRole.BUYER;
        int turnNumber = 0;

        System.out.println("[CHAT] ===== Payagent negotiation transcript =====");
        System.out.println("[CHAT] deal=" + AiTestFixtures.DEAL_ID
                + " buyerLimit=" + money(buyerMandate.limitPriceMinorUnits())
                + " sellerFloor=" + money(sellerMandate.limitPriceMinorUnits()));
        while (turnNumber < 6) {
            turnNumber++;
            MandateDTO mandate = turn == PartyRole.BUYER ? buyerMandate : sellerMandate;
            NegotiationPreferences preferences = turn == PartyRole.BUYER
                    ? buyerPreferences : sellerPreferences;
            NegotiationAgentService service = turn == PartyRole.BUYER ? buyer : seller;
            NegotiationContext context = NegotiationContext.of(
                    AiTestFixtures.DEAL_ID,
                    turn,
                    mandate,
                    AiTestFixtures.terms(mandate.targetPriceMinorUnits()),
                    history,
                    preferences);
            decision = service.decide(context, AiTestFixtures.NOW);
            abortIfRationaleUnavailable(decision);
            assertFalse(decision.warnings().contains("LLM_RATIONALE_INVALID"),
                    "live rationale was invalid on turn " + turnNumber);
            assertFalse(decision.warnings().contains("LLM_RATIONALE_REJECTED"),
                    "live rationale violated the output contract on turn " + turnNumber);
            System.out.println("[CHAT] " + turnNumber + ". " + turn + " -> " + decision.action()
                    + (decision.terms() == null ? "" : " at " + money(decision.terms().priceMinorUnits()))
                    + " | " + decision.rationaleSummary());

            if (decision.action() == NegotiationAction.ACCEPT
                    || decision.action() == NegotiationAction.STOP
                    || decision.action() == NegotiationAction.ESCALATE
                    || decision.action() == NegotiationAction.REJECT) {
                break;
            }
            assertNotNull(decision.terms(), "OPEN/COUNTER must carry terms");
            history.add(toOffer(decision, history.size() + 1));
            turn = turn == PartyRole.BUYER ? PartyRole.SELLER : PartyRole.BUYER;
        }
        System.out.println("[CHAT] ===== " + (decision == null ? "NO DECISION" : decision.action())
                + " after " + turnNumber + " turns; offers=" + history.size() + " =====");

        assertNotNull(decision);
        assertTrue(turnNumber >= 3, "the scenario must exercise multiple chat turns");
        assertEquals(NegotiationAction.ACCEPT, decision.action(),
                "expected the simulated parties to converge");
        assertNotNull(decision.terms());
        assertTrue(decision.terms().priceMinorUnits() >= sellerMandate.limitPriceMinorUnits());
        assertTrue(decision.terms().priceMinorUnits() <= buyerMandate.limitPriceMinorUnits());
        for (OfferDTO offer : history) {
            MandateDTO mandate = offer.proposerRole() == PartyRole.BUYER
                    ? buyerMandate : sellerMandate;
            assertTrue(new OfferConstraintValidator().inspect(
                    offer.proposerRole(), mandate, offer.terms(), AiTestFixtures.NOW).isEmpty(),
                    "offer went outside the proposer mandate: " + offer.offerId());
            assertFalse(offer.rationaleSummary().isBlank());
        }
        assertFalse(decision.rationaleSummary().contains("limitPriceMinorUnits"));
        assertFalse(decision.rationaleSummary().contains("targetPriceMinorUnits"));
    }

    private static NegotiationAgentService agent(ChatCompletionClient client) {
        return new NegotiationAgentService(
                new RuleBasedStrategySelector(),
                new RuleBasedStrategySelector(),
                new NegotiationPolicyEngine(),
                new MandateValidator(),
                new OfferConstraintValidator(),
                new StructuredTermsValidator(),
                client,
                new RationaleValidator(),
                512);
    }

    private static OfferDTO toOffer(ProposedOfferDTO decision, int round) {
        return new OfferDTO(
                "chat-offer-" + round,
                decision.dealId(),
                round,
                decision.proposerRole(),
                decision.generatedByAgent(),
                decision.terms(),
                decision.rationaleSummary(),
                BigDecimal.valueOf(decision.confidence()).toPlainString(),
                true,
                AiTestFixtures.NOW.plusSeconds(round * 60L).toString());
    }

    private static String money(long minorUnits) {
        return BigDecimal.valueOf(minorUnits, 2).toPlainString() + " USD";
    }

    private static ChatCompletionClient resilientClient() {
        OpenAiCompatibleChatClient delegate = liveClient();
        return new ChatCompletionClient() {
            @Override
            public boolean isConfigured() {
                return delegate.isConfigured();
            }

            @Override
            public String complete(String systemPrompt, String userPrompt, int maxTokens) {
                LlmCallException last = null;
                int maxAttempts = 3;
                for (int attempt = 0; attempt < maxAttempts; attempt++) {
                    try {
                        return delegate.complete(systemPrompt, userPrompt, maxTokens);
                    } catch (LlmCallException ex) {
                        last = ex;
                        if (!transientFailure(ex) || attempt == maxAttempts - 1) {
                            throw ex;
                        }
                        try {
                            Thread.sleep(retryDelayMillis(ex, attempt));
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new LlmCallException(
                                    AiNegotiationException.Code.LLM_UNAVAILABLE,
                                    "live test retry was interrupted", interrupted);
                        }
                    }
                }
                throw last;
            }
        };
    }

    private static void abortIfUnavailable(IntakeExtractionResult result) {
        if (result.source() != IntakeExtractionResult.Source.LLM
                && result.warnings().contains("LLM_UNAVAILABLE")) {
            throw new TestAbortedException(
                    "Gemini live test skipped: provider unavailable or quota exhausted");
        }
    }

    private static void abortIfRationaleUnavailable(ProposedOfferDTO decision) {
        if (decision.warnings().contains("LLM_RATIONALE_UNAVAILABLE")) {
            throw new TestAbortedException(
                    "Gemini live test skipped: provider unavailable or quota exhausted");
        }
    }

    private static boolean transientFailure(LlmCallException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        return message.contains("HTTP 503")
                || message.contains("HTTP 429")
                || message.contains("timed out");
    }

    private static long retryDelayMillis(LlmCallException ex, int attempt) {
        return 2_000L * (attempt + 1);
    }

    private static OpenAiCompatibleChatClient liveClient() {
        String apiKey = Objects.requireNonNull(
                System.getenv("SPRING_AI_OPENAI_API_KEY"),
                "SPRING_AI_OPENAI_API_KEY is required for the live test");
        String baseUrl = valueOrDefault(
                "SPRING_AI_OPENAI_BASE_URL",
                "https://generativelanguage.googleapis.com/v1beta/openai");
        String model = valueOrDefault("SPRING_AI_OPENAI_MODEL", "gemini-3.5-flash");
        return new OpenAiCompatibleChatClient(
                new ObjectMapper(), apiKey, baseUrl, model, 60_000L, 10_000L);
    }

    private static String valueOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
