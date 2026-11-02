package com.payagent.ai;

import com.payagent.ai.client.ChatCompletionClient;
import com.payagent.ai.intake.IntakeExtractionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelBenchmarkRunnerTest {

    private static final String VALID_JSON = """
            {
              "title": "Landing page",
              "deliverables": ["hero", "pricing table"],
              "priceMinorUnits": 240000,
              "currency": "USD",
              "deliveryDays": 21,
              "includedRevisions": 3,
              "acceptanceCriteriaText": "loads in modern browsers"
            }
            """;

    private static final String INVALID_JSON = "not-json";

    @Test
    void shipsTwentyDefaultPromptCases() {
        assertEquals(20, ModelBenchmarkRunner.cases().size());
    }
    @Test
    void distinguishesValidModelOutputFromFallback() {
        List<ModelBenchmarkRunner.PromptCase> cases = List.of(
                new ModelBenchmarkRunner.PromptCase(1, "Title: clear brief\nBudget: $2,400 USD\nDelivery: 21 days\nRevisions: 3 included\nAcceptance criteria: loads in modern browsers", "USD"),
                new ModelBenchmarkRunner.PromptCase(2, "Title: ambiguous brief\nBudget: $240 USD", "USD"));
        ModelBenchmarkRunner.Report report = ModelBenchmarkRunner.evaluate(
                stub(List.of(VALID_JSON, INVALID_JSON)), cases);

        assertEquals(2, report.summary().total());
        assertEquals(1, report.summary().valid());
        assertTrue(report.cases().get(0).valid());
        assertFalse(report.cases().get(1).valid());
        assertEquals(
                List.of("MODEL_NOT_USED", "INCOMPLETE_TERMS"),
                report.cases().get(1).issues());
        assertEquals(
                IntakeExtractionResult.Source.DETERMINISTIC,
                report.cases().get(1).source());
    }

    private static ChatCompletionClient stub(List<String> responses) {
        var iterator = responses.iterator();
        return new ChatCompletionClient() {
            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt, int maxTokens) {
                if (!iterator.hasNext()) {
                    throw new NoSuchElementException("stub exhausted");
                }
                return iterator.next();
            }
        };
    }
}
