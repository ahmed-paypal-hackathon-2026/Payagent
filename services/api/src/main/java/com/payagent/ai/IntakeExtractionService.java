package com.payagent.ai;

import com.payagent.common.contracts.StructuredTermsDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured deal-intake extractor (WP-C1).
 *
 * <p>Converts a raw natural-language project brief into a validated
 * {@link StructuredTermsDTO}. The extraction path is:
 * <ol>
 *   <li>Send the raw text to the LLM with a strict JSON-schema system instruction.</li>
 *   <li>Parse the model output as JSON.</li>
 *   <li>Validate every required field; reject malformed or incomplete output.</li>
 * </ol>
 *
 * <p>Trust boundary (AGENTS.md §4.8): the LLM output is a <em>proposed</em> DTO only.
 * It is validated here and, downstream, by Member A's MandateService and
 * UtilityCalculator. The LLM never mutates the database and never receives
 * PayPal credentials.
 *
 * <p>This class is intentionally not a Spring {@code @Service}: it is instantiated
 * with a {@link ChatClient} whose production implementation is Spring AI-backed
 * (OpenAI-compatible endpoint via {@code SPRING_AI_OPENAI_BASE_URL} /
 * {@code SPRING_AI_OPENAI_API_KEY}). Tests instantiate it with the deterministic
 * {@code FixtureChatClient} so the extraction path is exercised without external
 * credentials.
 */
public class IntakeExtractionService {

    /**
     * System instruction demanding strict JSON output matching the
     * StructuredTermsDTO schema. Field constraints mirror the canonical
     * contract (plan §3.2).
     */
    static final String SYSTEM_INSTRUCTION = """
            You are a structured deal-intake extractor. Convert the user's freelance \
            project brief into a single JSON object with EXACTLY these fields:
            {
              "title": string (non-empty, concise project title),
              "deliverables": string[] (ordered list of concrete deliverables),
              "priceMinorUnits": integer (price in minor currency units / cents, >= 0),
              "currency": string (ISO-4217, e.g. "USD"),
              "deliveryDays": integer (calendar days, >= 1),
              "includedRevisions": integer (>= 0),
              "acceptanceCriteriaText": string (non-empty acceptance criteria)
            }
            Respond with ONLY the JSON object. No markdown, no explanation, no \
            surrounding text. If a field cannot be determined, use a sensible \
            default and never leave it null.
            """;

    private final ChatClient chatClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public IntakeExtractionService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Extracts structured terms from a raw natural-language brief.
     *
     * @param rawText the user's raw project brief
     * @return a validated {@link StructuredTermsDTO}
     * @throws IntakeExtractionException if the model output is missing, malformed,
     *         incomplete, or fails schema validation
     */
    public StructuredTermsDTO extractDealIntake(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new IntakeExtractionException("Raw prompt must not be blank");
        }

        String modelOutput = chatClient.call(SYSTEM_INSTRUCTION, rawText);
        if (modelOutput == null || modelOutput.isBlank()) {
            throw new IntakeExtractionException("Model returned no output");
        }

        String json = stripMarkdownFences(modelOutput);
        JsonNode root = parseJson(json);
        return validateAndBuild(root);
    }

    /**
     * Strips optional markdown code fences some models wrap around JSON.
     */
    private String stripMarkdownFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }
        return trimmed;
    }

    private JsonNode parseJson(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new IntakeExtractionException("Model output is not valid JSON: " + json, e);
        }
    }

    private StructuredTermsDTO validateAndBuild(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IntakeExtractionException("Model output must be a JSON object");
        }

        String title = requireNonBlankText(root, "title");
        List<String> deliverables = requireDeliverables(root);
        long priceMinorUnits = requireNonNegativeLong(root, "priceMinorUnits");
        String currency = requireNonBlankText(root, "currency");
        int deliveryDays = requirePositiveInt(root, "deliveryDays");
        int includedRevisions = requireNonNegativeInt(root, "includedRevisions");
        String acceptanceCriteriaText = requireNonBlankText(root, "acceptanceCriteriaText");

        return new StructuredTermsDTO(
                title, deliverables, priceMinorUnits, currency,
                deliveryDays, includedRevisions, acceptanceCriteriaText);
    }

    private String requireNonBlankText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new IntakeExtractionException("Missing or blank field: " + field);
        }
        return node.asText();
    }

    private List<String> requireDeliverables(JsonNode root) {
        JsonNode node = root.get("deliverables");
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new IntakeExtractionException("Missing or empty array field: deliverables");
        }
        List<String> deliverables = new ArrayList<>();
        for (JsonNode element : node) {
            if (!element.isTextual() || element.asText().isBlank()) {
                throw new IntakeExtractionException("deliverables must contain only non-blank strings");
            }
            deliverables.add(element.asText());
        }
        return deliverables;
    }

    private long requireNonNegativeLong(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.canConvertToLong() || node.longValue() < 0) {
            throw new IntakeExtractionException("Missing or negative field: " + field);
        }
        return node.longValue();
    }

    private int requirePositiveInt(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.canConvertToInt() || node.intValue() < 1) {
            throw new IntakeExtractionException("Missing or non-positive field: " + field);
        }
        return node.intValue();
    }

    private int requireNonNegativeInt(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.canConvertToInt() || node.intValue() < 0) {
            throw new IntakeExtractionException("Missing or negative field: " + field);
        }
        return node.intValue();
    }

    /**
     * Thrown when the model output cannot be converted into a valid
     * {@link StructuredTermsDTO}.
     */
    public static class IntakeExtractionException extends RuntimeException {
        public IntakeExtractionException(String message) {
            super(message);
        }

        public IntakeExtractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
