package com.payagent.ai.client;

/**
 * Minimal LLM boundary used by Member C.
 *
 * <p>The interface is deliberately provider-neutral so the current
 * OpenAI-compatible HTTP adapter can later be replaced by Spring AI's
 * {@code ChatClient} without changing intake or negotiation services.</p>
 */
public interface ChatCompletionClient {

    boolean isConfigured();

    /**
     * @return the assistant message content
     * @throws LlmCallException when the endpoint is unavailable, times out or
     *                          returns an unusable response
     */
    String complete(String systemPrompt, String userPrompt, int maxTokens);
}
