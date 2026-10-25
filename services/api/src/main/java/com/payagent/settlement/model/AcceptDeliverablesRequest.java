package com.payagent.settlement.model;

import jakarta.validation.constraints.Min;

public record AcceptDeliverablesRequest(
        @Min(1) int evidenceVersion) {}
