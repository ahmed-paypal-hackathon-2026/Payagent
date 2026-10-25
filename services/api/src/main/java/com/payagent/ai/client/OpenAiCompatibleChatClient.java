package com.payagent.ai.client;

import com.payagent.ai.AiNegotiationException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Small OpenAI-compatible chat-completions adapter.
 *
 * <p>Why not Spring AI? Spring AI 1.0's starters depend on Spring Boot 3.4.x,
 * while this repository is pinned to Spring Boot 3.3.5. Upgrading the shared
 * framework is outside Member C's ownership and risks the rest of the team. This
 * adapter keeps the exact interface needed (system/user message -> text), has no
 * extra dependency, and can be swapped for a Spring AI {@code ChatClient}
 * implementation later without touching callers.</p>
 */
@Component
public class OpenAiCompatibleChatClient implements ChatCompletionClient {

    private final ObjectMapper mapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final long timeoutMillis;
    private final HttpClient httpClient;

    public OpenAiCompatibleChatClient(
            ObjectMapper mapper,
            @Value("${payagent.spring-ai.api-key:}") String apiKey,
            @Value("${payagent.spring-ai.base-url:}") String baseUrl,
            @Value("${payagent.spring-ai.model:gpt-4o-mini}") String model,
            @Value("${payagent.ai.request-timeout-ms:6000}") long timeoutMillis,
            @Value("${payagent.ai.connect-timeout-ms:2000}") long connectTimeoutMillis) {
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.model = model == null ? "" : model.trim();
        this.timeoutMillis = Math.max(500, timeoutMillis);
        long connect = Math.max(250, connectTimeoutMillis);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connect))
                .build();
    }

    @Override
    public boolean isConfigured() {
        return !baseUrl.isEmpty() && !model.isEmpty();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, int maxTokens) {
        if (!isConfigured()) {
            throw new LlmCallException(
                    AiNegotiationException.Code.LLM_UNAVAILABLE,
                    "LLM endpoint is not configured");
        }
        if (systemPrompt == null || userPrompt == null) {
            throw new LlmCallException(
                    AiNegotiationException.Code.LLM_RESPONSE_INVALID,
                    "prompts must not be null");
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.0);
        body.put("max_tokens", Math.max(32, maxTokens));
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl()))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("Content-Type", "application/json")
                .header("User-Agent", "payagent-ai/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (!apiKey.isEmpty()) {
            request.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException ex) {
            throw new LlmCallException(
                    AiNegotiationException.Code.LLM_UNAVAILABLE, "LLM request timed out");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LlmCallException(
                    AiNegotiationException.Code.LLM_UNAVAILABLE, "LLM request was interrupted");
        } catch (Exception ex) {
            throw new LlmCallException(
                    AiNegotiationException.Code.LLM_UNAVAILABLE,
                    "LLM request failed: " + ex.getClass().getSimpleName(), ex);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new LlmCallException(
                    AiNegotiationException.Code.LLM_UNAVAILABLE,
                    "LLM endpoint returned HTTP " + response.statusCode());
        }
        try {
            JsonNode root = mapper.readTree(response.body());
            JsonNode content = root.at("/choices/0/message/content");
            if (!content.isTextual() || content.textValue().isBlank()) {
                throw new LlmCallException(
                        AiNegotiationException.Code.LLM_RESPONSE_INVALID,
                        "LLM response did not contain message content");
            }
            return content.textValue();
        } catch (LlmCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LlmCallException(
                    AiNegotiationException.Code.LLM_RESPONSE_INVALID,
                    "LLM response was not valid JSON", ex);
        }
    }

    private String chatCompletionsUrl() {
        String normalized = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }
}
