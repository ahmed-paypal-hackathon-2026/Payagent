package com.payagent.ai.evaluation;

import com.payagent.ai.negotiation.NegotiationPreferences;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates deterministic synthetic scenarios for training and evaluation.
 *
 * <p>This is simulation data, not observed human negotiations. It is clearly
 * labelled as such in the model artifact and the benchmark document. The
 * generator deliberately covers buyer and seller roles, preference archetypes,
 * narrow and wide mandate zones, short and long round budgets, cooperative and
 * stubborn counterparties, and a small share of impossible deals.</p>
 */
public final class SyntheticScenarioGenerator {

    public static final String DATASET_NAME =
            "synthetic-v1 (deterministic simulated negotiations; not human data)";
    private static final String EXPIRES_AT = "2030-01-01T00:00:00Z";
    private static final double[][] PREFERENCE_ARCHETYPES = {
            {0.95, 0.10, 0.25, 0.80},
            {0.80, 0.20, 0.15, 0.75},
            {0.10, 0.95, 0.90, 0.20},
            {0.50, 0.35, 0.50, 0.50},
            {0.30, 0.60, 0.90, 0.25},
            {0.85, 0.85, 0.50, 0.60},
            {0.20, 0.25, 0.35, 0.25},
            {0.70, 0.65, 0.75, 0.55}
    };

    public List<SyntheticScenario> generate(int count, long seed) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }
        Random random = new Random(seed);
        List<SyntheticScenario> scenarios = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            scenarios.add(generateOne(random, seed, i));
        }
        return List.copyOf(scenarios);
    }

    private SyntheticScenario generateOne(Random random, long seed, int index) {
        PartyRole role = random.nextBoolean() ? PartyRole.BUYER : PartyRole.SELLER;
        NegotiationPreferences preferences = preferences(random);
        long base = roundToHundreds(100_000L + random.nextInt(400) * 1_000L);
        double zoneRatio = 0.06 + 0.39 * random.nextDouble();
        long zone = Math.max(2_000L, Math.round(base * zoneRatio));
        long limit;
        long target;
        if (role == PartyRole.BUYER) {
            limit = base;
            target = Math.max(1_000L, base - zone);
        } else {
            target = base;
            limit = Math.max(1_000L, base - zone);
        }

        int maxRounds = 4 + random.nextInt(8);
        String id = "synthetic-" + seed + "-" + index;
        MandateDTO mandate = new MandateDTO(
                "mandate-" + id,
                id,
                role,
                1,
                "USD",
                limit,
                target,
                role == PartyRole.BUYER ? 30 : 14,
                21,
                2,
                2,
                List.of("source code"),
                List.of("crypto payment"),
                NegotiationMode.AUTONOMOUS_BOUNDED,
                maxRounds,
                EXPIRES_AT);

        StructuredTermsDTO baseTerms = new StructuredTermsDTO(
                "Synthetic website project",
                List.of("source code", "documentation"),
                target,
                "USD",
                21,
                2,
                "The buyer confirms that the source code and documentation are complete.");

        boolean impossible = random.nextDouble() < 0.08;
        long counterpartyFloor = 0L;
        long counterpartyTarget = 0L;
        long counterpartyOpening = 0L;
        long counterpartyCeiling = 0L;
        if (role == PartyRole.BUYER) {
            double floorFactor = 0.72 + 0.30 * random.nextDouble();
            if (impossible) {
                floorFactor = 1.02 + 0.12 * random.nextDouble();
            }
            counterpartyFloor = roundToHundreds(limit * floorFactor);
            counterpartyTarget = roundToHundreds(counterpartyFloor * (1.06 + 0.22 * random.nextDouble()));
            counterpartyOpening = roundToHundreds(counterpartyTarget * (1.00 + 0.18 * random.nextDouble()));
        } else {
            double ceilingFactor = 0.90 + 0.35 * random.nextDouble();
            if (impossible) {
                ceilingFactor = 0.80 + 0.08 * random.nextDouble();
            }
            counterpartyCeiling = roundToHundreds(target * ceilingFactor);
            counterpartyTarget = roundToHundreds(counterpartyCeiling * (0.78 + 0.18 * random.nextDouble()));
            counterpartyOpening = roundToHundreds(counterpartyTarget * (0.85 + 0.12 * random.nextDouble()));
        }

        return new SyntheticScenario(
                id,
                role,
                preferences,
                mandate,
                baseTerms,
                random.nextDouble() < 0.80,
                counterpartyFloor,
                counterpartyTarget,
                counterpartyOpening,
                counterpartyCeiling,
                0.25 + 0.60 * random.nextDouble(),
                0.50 + 1.50 * random.nextDouble());
    }

    private static NegotiationPreferences preferences(Random random) {
        if (random.nextDouble() < 0.15) {
            return new NegotiationPreferences(
                    random.nextDouble(),
                    random.nextDouble(),
                    random.nextDouble(),
                    random.nextDouble());
        }
        double[] archetype = PREFERENCE_ARCHETYPES[random.nextInt(PREFERENCE_ARCHETYPES.length)];
        return new NegotiationPreferences(
                clamp01(archetype[0] + random.nextGaussian() * 0.06),
                clamp01(archetype[1] + random.nextGaussian() * 0.06),
                clamp01(archetype[2] + random.nextGaussian() * 0.06),
                clamp01(archetype[3] + random.nextGaussian() * 0.06));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static long roundToHundreds(double value) {
        return Math.max(100L, Math.round(value / 100.0) * 100L);
    }
}
