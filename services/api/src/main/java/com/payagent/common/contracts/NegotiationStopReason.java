package com.payagent.common.contracts;

public enum NegotiationStopReason {
    NONE,
    CONVERGED,
    NO_ZOPA_DETECTED,
    MAX_ROUNDS_REACHED,
    MANDATE_EXPIRED,
    USER_PAUSED,
    POLICY_VIOLATION_BLOCKED
}
