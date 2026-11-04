package com.payagent.agreements;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.payagent.common.contracts.StructuredTermsDTO;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

/**
 * RFC 8785 JSON Canonicalization Scheme implementation.
 *
 * <p>Produces canonical UTF-8 JSON for deterministic agreement hashing.
 * Rules applied:
 * <ul>
 *   <li>Object members are sorted lexicographically by key (Unicode code point).</li>
 *   <li>Numbers with no fractional part are serialized as integers (no decimal point).</li>
 *   <li>Numbers with a fractional part or exponent are serialized in canonical decimal form.</li>
 *   <li>Strings are JSON-escaped per RFC 8259; non-ASCII characters are preserved as UTF-8.</li>
 *   <li>Arrays preserve element order; elements are canonicalized recursively.</li>
 *   <li>Output is compact: no insignificant whitespace.</li>
 * </ul>
 *
 * <p>Note on the plan/contract naming discrepancy (ISSUE-004): plan v2.docx Section 2.2 refers to
 * {@code AGREEMENT_CANDIDATE} while the canonical {@code DealStatus} enum defines
 * {@code TERMS_PENDING_ACCEPTANCE}. This canonicalizer has no dependency on that naming; it
 * canonicalizes the terms payload only.
 */
public final class Rfc8785Canonicalizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Rfc8785Canonicalizer() {
    }

    /**
     * Canonicalizes a {@link StructuredTermsDTO} to RFC 8785 JSON.
     */
    public static String canonicalize(StructuredTermsDTO terms) {
        try {
            JsonNode tree = MAPPER.valueToTree(terms);
            StringWriter writer = new StringWriter();
            try (JsonGenerator gen = MAPPER.getFactory().createGenerator(writer)) {
                gen.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
                writeCanonical(gen, tree);
            }
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to canonicalize terms", e);
        }
    }

    /**
     * Returns the RFC 8785 canonical UTF-8 bytes for a {@link StructuredTermsDTO}.
     */
    public static byte[] canonicalBytes(StructuredTermsDTO terms) {
        return canonicalize(terms).getBytes(StandardCharsets.UTF_8);
    }

    private static void writeCanonical(JsonGenerator gen, JsonNode node) throws IOException {
        switch (node.getNodeType()) {
            case OBJECT -> {
                gen.writeStartObject();
                TreeSet<String> sortedKeys = new TreeSet<>();
                Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    sortedKeys.add(entry.getKey());
                }
                for (String key : sortedKeys) {
                    gen.writeFieldName(key);
                    writeCanonical(gen, node.get(key));
                }
                gen.writeEndObject();
            }
            case ARRAY -> {
                gen.writeStartArray();
                for (JsonNode element : node) {
                    writeCanonical(gen, element);
                }
                gen.writeEndArray();
            }
            case NUMBER -> writeNumber(gen, node);
            case STRING -> gen.writeString(node.asText());
            case BOOLEAN -> gen.writeBoolean(node.asBoolean());
            case NULL -> gen.writeNull();
            case MISSING, POJO, BINARY -> {
                // Not expected for canonical terms; fall back to Jackson serialization.
                gen.writeRawValue(node.toString());
            }
        }
    }

    private static void writeNumber(JsonGenerator gen, JsonNode node) throws IOException {
        BigDecimal decimal = node.decimalValue();
        // RFC 8785: numbers without a fractional part are integers.
        if (decimal.scale() <= 0 || decimal.stripTrailingZeros().scale() <= 0) {
            gen.writeNumber(decimal.longValue());
        } else {
            gen.writeNumber(decimal);
        }
    }
}
