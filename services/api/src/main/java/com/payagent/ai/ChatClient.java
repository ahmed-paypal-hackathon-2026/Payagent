package com.payagent.ai;

import com.payagent.common.contracts.StructuredTermsDTO;

/**
 * Abstraction over the LLM chat client used for structured extraction.
 *
 * <p>Production implementation plugs into Spring AI's {@code ChatClient}
 * (OpenAI-compatible endpoint configured via {@code SPRING_AI_OPENAI_BASE_URL}
 * and {@code SPRING_AI_OPENAI_API_KEY}). The test implementation is a deterministic
 * fixture-backed client so the extraction path can be exercised without external
 * credentials.
 *
 * <p>Member C owns this package per AGENTS.md §2. The LLM never receives PayPal
 * credentials, never computes authoritative utility scores, and never mutates the
 * database directly (AGENTS.md §4.8).
 */
public interface ChatClient {

    /**
     * Sends a prompt to the model and returns the raw text response.
     *
     * @param systemInstruction the system instruction describing the extraction task
     * @param userMessage the raw user input to extract structured terms from
     * @return the model's raw text output (expected to be a JSON object)
     */
    String call(String systemInstruction, String userMessage);
}
