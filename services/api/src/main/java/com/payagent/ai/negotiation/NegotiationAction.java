package com.payagent.ai.negotiation;

/**
 * Actions the negotiation component may propose. The surrounding application
 * (Member A's state machine) remains the authority that persists/executes them.
 */
public enum NegotiationAction {
    /** No prior offer exists; create the opening offer. */
    OPEN,
    /** Respond with an adjusted, mandate-compliant counteroffer. */
    COUNTER,
    /** Recommend accepting the counterparty offer; {@code requiresHumanApproval} may still be set. */
    ACCEPT,
    /** Reject the counterparty offer without proposing terms. */
    REJECT,
    /** Human intervention is required; do not fabricate a deal. */
    ESCALATE,
    /** End the automated negotiation without agreement. */
    STOP
}
