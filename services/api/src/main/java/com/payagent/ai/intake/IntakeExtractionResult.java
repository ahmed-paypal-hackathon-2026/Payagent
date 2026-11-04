package com.payagent.ai.intake;

import com.payagent.common.contracts.StructuredTermsDTO;

import java.util.List;

/**
 * Diagnostics for intake extraction. {@code complete=false} means one or more
 * fields were not found and safe, flagged defaults were used; the UI/domain
 * layer should ask the user to confirm before locking a deal.
 */
public record IntakeExtractionResult(
        StructuredTermsDTO terms,
        Source source,
        boolean complete,
        List<String> warnings,
        long latencyMillis) {

    public enum Source {
        LLM,
        DETERMINISTIC
    }

    public IntakeExtractionResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public IntakeExtractionResult withLatency(long millis) {
        return new IntakeExtractionResult(terms, source, complete, warnings, millis);
    }
}
