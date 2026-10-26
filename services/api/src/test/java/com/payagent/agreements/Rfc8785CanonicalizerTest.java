package com.payagent.agreements;

import com.payagent.common.contracts.StructuredTermsDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Rfc8785CanonicalizerTest {

    private static final List<String> DELIVERED = List.of("figma", "source", "notes");

    private static StructuredTermsDTO terms(long price, int delivery, int revisions) {
        return new StructuredTermsDTO("Design sprint", DELIVERED, price, "USD", delivery, revisions, "Lighthouse > 90");
    }

    @Test
    void canonicalize_producesSortedCompactJson() {
        StructuredTermsDTO t = terms(80000L, 10, 3);
        String canonical = Rfc8785Canonicalizer.canonicalize(t);
        assertEquals("{\"acceptanceCriteriaText\":\"Lighthouse > 90\",\"currency\":\"USD\",\"deliverables\":[\"figma\",\"source\",\"notes\"],\"deliveryDays\":10,\"includedRevisions\":3,\"priceMinorUnits\":80000,\"title\":\"Design sprint\"}", canonical);
        assertArrayEquals(Rfc8785Canonicalizer.canonicalBytes(t), Rfc8785Canonicalizer.canonicalBytes(t));
    }

    @Test
    void canonicalize_changedTermsProducesDifferentHashInput() {
        assertNotEquals(Rfc8785Canonicalizer.canonicalize(terms(80000L, 10, 3)), Rfc8785Canonicalizer.canonicalize(terms(85000L, 12, 2)));
    }

    @Test
    void canonicalize_escapeSpecialCharacters() {
        StructuredTermsDTO t = new StructuredTermsDTO("title \"quoted\"", List.of("a\\b"), 1L, "USD", 1, 0, "criteria \"x\"");
        String canonical = Rfc8785Canonicalizer.canonicalize(t);
        assertEquals("{\"acceptanceCriteriaText\":\"criteria \\\"x\\\"\",\"currency\":\"USD\",\"deliverables\":[\"a\\\\b\"],\"deliveryDays\":1,\"includedRevisions\":0,\"priceMinorUnits\":1,\"title\":\"title \\\"quoted\\\"\"}", canonical);
    }

    @Test
    void canonicalize_partialRfc8785Behaviors() {
        String zeroPrice = Rfc8785Canonicalizer.canonicalize(terms(0L, 1, 0));
        assertTrue(zeroPrice.contains("\"priceMinorUnits\":0"));
        assertFalse(zeroPrice.contains("\"priceMinorUnits\":0.0"));

        StructuredTermsDTO unicodeTerms = new StructuredTermsDTO("caf\u00e9", List.of("figma"), 100L, "USD", 5, 2, "criteria \u00e9");
        String unicodeCanonical = Rfc8785Canonicalizer.canonicalize(unicodeTerms);
        assertTrue(unicodeCanonical.contains("\u00e9"));
        assertFalse(unicodeCanonical.contains("\\u00e9"));

        String sorted = Rfc8785Canonicalizer.canonicalize(terms(80000L, 10, 3));
        String[] keys = {"acceptanceCriteriaText", "currency", "deliverables", "deliveryDays", "includedRevisions", "priceMinorUnits", "title"};
        for (int i = 0; i < keys.length - 1; i++) {
            int pos1 = sorted.indexOf("\"" + keys[i] + "\":");
            int pos2 = sorted.indexOf("\"" + keys[i + 1] + "\":");
            assertTrue(pos1 < pos2, "Key " + keys[i] + " should precede " + keys[i + 1]);
        }
        // numeric serialization: no scientific notation for long values
        StructuredTermsDTO large = terms(9_999_999_999L, 1, 0);
        String largeJson = Rfc8785Canonicalizer.canonicalize(large);
        assertTrue(largeJson.contains("\"priceMinorUnits\":9999999999"));
        assertFalse(largeJson.contains("E"));
    }
}
