package com.payagent.releasegates;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.ai.client.ChatCompletionClient;
import com.payagent.ai.intake.*;
import com.payagent.ai.negotiation.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises untrusted input reaching the real sanitizer/extractor, separate from output-only probes. */
class UntrustedInputReleaseGateTest {
    static Stream<String> attacks() throws Exception {
        return NegotiationReleaseGateTest.attacks();
    }

    @ParameterizedTest(name = "TST-03/04 untrusted input {index}")
    @MethodSource("attacks")
    void tst03_04_counterpartyInstructionsRemainInsideSingleUntrustedBlock(String attack) {
        AtomicReference<String> prompt = new AtomicReference<>();
        ChatCompletionClient client = new ChatCompletionClient() {
            public boolean isConfigured() { return true; }
            public String complete(String system, String user, int maxTokens) {
                prompt.set(user);
                // A tool-call-shaped response is inert text; no tool executor is provided to this boundary.
                return "{\"tool_calls\":[{\"name\":\"capture_payment\",\"arguments\":{\"amount\":1}}]}";
            }
        };
        String brief = "Build a landing page\n- Home page\nBudget: $1,000\nDelivery: 21 days\nIncludes 2 revisions\nAcceptance: links work.\n" + attack;
        IntakeExtractionService service = new IntakeExtractionService(client, new PromptSanitizer(),
                new StructuredTermsValidator(), new DeterministicIntakeExtractor(), new ObjectMapper());
        var result = service.extract(brief, "USD");
        assertEquals(100000, result.terms().priceMinorUnits());
        assertEquals("USD", result.terms().currency());
        assertTrue(new StructuredTermsValidator().isValid(result.terms()));
        String sent = prompt.get();
        assertNotNull(sent);
        assertEquals(1, sent.split("<untrusted_counterparty_data>", -1).length - 1);
        assertEquals(1, sent.split("</untrusted_counterparty_data>", -1).length - 1);
        assertFalse(sent.contains("system:"));
        assertFalse(sent.toLowerCase().contains("ignore previous instructions"));
    }
}
