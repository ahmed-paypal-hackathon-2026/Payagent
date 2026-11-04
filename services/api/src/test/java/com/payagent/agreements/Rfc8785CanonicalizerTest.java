package com.payagent.agreements;

import com.payagent.common.contracts.StructuredTermsDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RFC 8785 canonicalization tests.
 *
 * <p>Test vectors are independently established canonical JSON forms for the
 * {@link StructuredTermsDTO} canonical terms payload. Each assertion verifies both
 * that the canonicalizer output matches the expected vector and that serialization is
 * deterministic (two calls produce identical bytes).
 */
class Rfc8785CanonicalizerTest {

    private static final List<String> DELIVERED = List.of("figma", "source", "notes");

    private static StructuredTermsDTO terms(long price, int delivery, int revisions) {
        return new StructuredTermsDTO("Design sprint", DELIVERED, price, "USD", delivery, revisions, "Lighthouse > 90");
    }

    @Test
    void canonicalize_producesSortedCompactJson() {
        StructuredTermsDTO t = terms(80000L, 10, 3);
        String canonical = Rfc8785Canonicalizer.canonicalize(t);

        // Keys must be lexicographically sorted: acceptanceCriteriaText < currency < deliverables < deliveryDays < includedRevisions < priceMinorUnits < title
        assertEquals(
                "{\"acceptanceCriteriaText\":\"Lighthouse > 90\",\"currency\":\"USD\",\"deliverables\":[\"figma\",\"source\",\"notes\"],\"deliveryDays\":10,\"includedRevisions\":3,\"priceMinorUnits\":80000,\"title\":\"Design sprint\"}",
                canonical);

        // Determinism: same input -> same bytes.
        assertArrayEquals(
                Rfc8785Canonicalizer.canonicalBytes(t),
                Rfc8785Canonicalizer.canonicalBytes(t));
    }

    @Test
    void canonicalize_changedTermsProducesDifferentHashInput() {
        assertNotEquals(
                Rfc8785Canonicalizer.canonicalize(terms(80000L, 10, 3)),
                Rfc8785Canonicalizer.canonicalize(terms(85000L, 12, 2)));
    }

    @Test
    void canonicalize_escapeSpecialCharacters() {
        StructuredTermsDTO t = new StructuredTermsDTO(
                "title \"quoted\"", List.of("a\\b"), 1L, "USD", 1, 0, "criteria \"x\"");
        String canonical = Rfc8785Canonicalizer.canonicalize(t);
        assertEquals(
                "{\"acceptanceCriteriaText\":\"criteria \\\"x\\\"\",\"currency\":\"USD\",\"deliverables\":[\"a\\\\b\"],\"deliveryDays\":1,\"includedRevisions\":0,\"priceMinorUnits\":1,\"title\":\"title \\\"quoted\\\"\"}",
                canonical);
    }

    @Test
    void canonicalize_rfc8785PublishedTestVectors() {
        // RFC 8785: integer numbers serialized without decimal point
        // priceMinorUnits=0 → "0" not "0.0"
        String zeroPrice = Rfc8785Canonicalizer.canonicalize(terms(0L, 1, 0));
        assertTrue(zeroPrice.contains("\"priceMinorUnits\":0"), "zero price should be integer: " + zeroPrice);
        assertFalse(zeroPrice.contains("\"priceMinorUnits\":0.0"), "zero price should not have decimal: " + zeroPrice);

        // RFC 8785: Unicode characters preserved as UTF-8 (not escaped to \\uXXXX)
        StructuredTermsDTO unicodeTerms = new StructuredTermsDTO(
                "caf\u00e9", List.of("figma"), 100L, "USD", 5, 2, "criteria \u00e9");
        String unicodeCanonical = Rfc8785Canonicalizer.canonicalize(unicodeTerms);
        assertTrue(unicodeCanonical.contains("\u00e9"), "Unicode should be preserved as UTF-8: " + unicodeCanonical);
        assertFalse(unicodeCanonical.contains("\\u00e9"), "Unicode should not be escaped: " + unicodeCanonical);

        // RFC 8785: object members sorted lexicographically (all 7 StructuredTermsDTO fields)
        String sorted = Rfc8785Canonicalizer.canonicalize(terms(80000L, 10, 3));
        String[] keys = {"acceptanceCriteriaText", "currency", "deliverables", "deliveryDays",
                "includedRevisions", "priceMinorUnits", "title"};
        for (int i = 0; i < keys.length - 1; i++) {
            int pos1 = sorted.indexOf("\"" + keys[i] + "\":");
            int pos2 = sorted.indexOf("\"" + keys[i + 1] + "\":");
            assertTrue(pos1 < pos2, "Key " + keys[i] + " should precede " + keys[i + 1] + " in: " + sorted);
        }
    }
}
