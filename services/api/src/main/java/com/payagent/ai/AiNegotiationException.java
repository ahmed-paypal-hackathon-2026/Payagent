package com.payagent.ai;

/**
 * Controlled failure raised by the negotiation-intelligence component.
 *
 * <p>Callers (Member A's deal service) should translate {@link Code} into an
 * appropriate HTTP response. No exception message contains secrets or
 * counterparty mandate values.</p>
 */
public class AiNegotiationException extends RuntimeException {

    public enum Code {
        INVALID_MANDATE,
        MANDATE_EXPIRED,
        INVALID_PREFERENCES,
        INVALID_TERMS,
        INTAKE_EXTRACTION_FAILED,
        INVALID_STATE,
        LLM_UNAVAILABLE,
        LLM_RESPONSE_INVALID
    }

    private final Code code;

    public AiNegotiationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public AiNegotiationException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
