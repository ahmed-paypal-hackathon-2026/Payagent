package com.payagent.ai.client;

import com.payagent.ai.AiNegotiationException;

/** Failure at the model boundary. Messages never contain API keys or prompts. */
public class LlmCallException extends AiNegotiationException {

    public LlmCallException(Code code, String message) {
        super(code, message);
    }

    public LlmCallException(Code code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
