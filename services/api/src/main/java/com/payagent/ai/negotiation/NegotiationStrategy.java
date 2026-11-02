package com.payagent.ai.negotiation;

/**
 * Explicit, distinguishable negotiation archetypes. Parameters are normalized
 * to the caller's price range between target and reservation limit.
 *
 * <p>These are not cosmetic labels: each archetype changes the opening anchor,
 * the concession budget, the concession speed and the acceptance threshold.</p>
 */
public enum NegotiationStrategy {

    /** Hold near the target, concede slowly, accept only close to the target. */
    PATIENT_PRICE("Patient price-maximizing", 0.85, 0.42, 0.35, 0.72),

    /** Very small concessions and a demanding acceptance threshold. */
    FIRM("Firm", 0.90, 0.28, 0.22, 0.86),

    /** Neutral defaults with moderate movement. */
    BALANCED("Balanced", 0.60, 0.62, 0.55, 0.50),

    /** Concedes readily but starts cautiously; suitable for uncertain demand. */
    FLEXIBLE("Flexible", 0.45, 0.85, 0.78, 0.34),

    /** Opens near the target and moves quickly toward the reservation limit. */
    FAST_SALE("Fast sale", 0.30, 0.95, 1.10, 0.15);

    private final String displayName;
    private final double openingAggressiveness;
    private final double maxConcessionFraction;
    private final double concessionRate;
    private final double acceptanceShare;

    NegotiationStrategy(
            String displayName,
            double openingAggressiveness,
            double maxConcessionFraction,
            double concessionRate,
            double acceptanceShare) {
        this.displayName = displayName;
        this.openingAggressiveness = openingAggressiveness;
        this.maxConcessionFraction = maxConcessionFraction;
        this.concessionRate = concessionRate;
        this.acceptanceShare = acceptanceShare;
    }

    public String displayName() {
        return displayName;
    }

    public double openingAggressiveness() {
        return openingAggressiveness;
    }

    public double maxConcessionFraction() {
        return maxConcessionFraction;
    }

    public double concessionRate() {
        return concessionRate;
    }

    public double acceptanceShare() {
        return acceptanceShare;
    }
}
