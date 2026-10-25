package com.payagent.ai.strategy;

import com.payagent.ai.negotiation.NegotiationContext;

/** Selects a negotiation archetype from observable context only. */
@FunctionalInterface
public interface StrategySelector {
    StrategySelection select(NegotiationContext context);
}
