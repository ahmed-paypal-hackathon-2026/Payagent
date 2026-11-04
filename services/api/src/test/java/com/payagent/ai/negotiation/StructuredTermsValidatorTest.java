package com.payagent.ai.negotiation;

import com.payagent.ai.AiNegotiationException;
import com.payagent.ai.AiTestFixtures;
import com.payagent.common.contracts.StructuredTermsDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredTermsValidatorTest {

    private final StructuredTermsValidator validator = new StructuredTermsValidator();

    @Test
    void acceptsCanonicalTerms() {
        assertDoesNotThrow(() -> validator.validate(AiTestFixtures.terms(90_000L)));
        assertTrue(validator.isValid(AiTestFixtures.terms(90_000L)));
    }

    @Test
    void rejectsStructuralProblems() {
        assertInvalid(new StructuredTermsDTO(
                " ", List.of("a"), 1L, "USD", 1, 0, "done"));
        assertInvalid(new StructuredTermsDTO(
                "title", List.of(), 1L, "USD", 1, 0, "done"));
        assertInvalid(new StructuredTermsDTO(
                "title", List.of("same", "SAME"), 1L, "USD", 1, 0, "done"));
        assertInvalid(new StructuredTermsDTO(
                "title", List.of("a"), -1L, "USD", 1, 0, "done"));
        assertInvalid(new StructuredTermsDTO(
                "title", List.of("a"), 1L, "US", 1, 0, "done"));
        assertInvalid(new StructuredTermsDTO(
                "title", List.of("a"), 1L, "USD", 0, 0, "done"));
        assertInvalid(new StructuredTermsDTO(
                "title", List.of("a"), 1L, "USD", 1, -1, "done"));
        assertInvalid(new StructuredTermsDTO(
                "title", List.of("a"), 1L, "USD", 1, 0, " "));
    }

    @Test
    void reportsIssues() {
        assertTrue(validator.issues(AiTestFixtures.terms(90_000L)).isEmpty());
        assertFalse(validator.issues(new StructuredTermsDTO(
                "title", List.of("a"), 1L, "USD", 1, 0, " ")).isEmpty());
    }

    private void assertInvalid(StructuredTermsDTO terms) {
        assertFalse(validator.isValid(terms));
        AiNegotiationException error = assertThrows(
                AiNegotiationException.class, () -> validator.validate(terms));
        assertEquals(AiNegotiationException.Code.INVALID_TERMS, error.code());
    }
}
