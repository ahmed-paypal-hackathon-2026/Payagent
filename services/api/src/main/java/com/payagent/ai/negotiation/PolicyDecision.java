package com.payagent.ai.negotiation;

import com.payagent.common.contracts.StructuredTermsDTO;

import java.util.List;

/** Internal result of deterministic policy evaluation before LLM rationale enhancement. */
public record PolicyDecision(
        NegotiationAction action,
        StructuredTermsDTO terms,
        String rationale,
        List<String> warnings,
        String reasonCode,
        boolean fallbackUsed) {

    public PolicyDecision {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        rationale = rationale == null ? "" : rationale;
    }
}
