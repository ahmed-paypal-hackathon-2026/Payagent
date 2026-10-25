package com.payagent.ai.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.ai.AiNegotiationException;
import com.payagent.ai.AiTestFixtures;
import com.payagent.ai.negotiation.NegotiationPreferences;
import com.payagent.ai.negotiation.NegotiationStrategy;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyModelTest {

    private static final List<NegotiationStrategy> LABELS = List.of(
            NegotiationStrategy.PATIENT_PRICE,
            NegotiationStrategy.FIRM,
            NegotiationStrategy.BALANCED,
            NegotiationStrategy.FLEXIBLE,
            NegotiationStrategy.FAST_SALE);

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void loadsTheCommittedArtifactAndPredictsNormalizedProbabilities() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/ai/strategy-selector-v1.json")) {
            assertNotNull(in, "committed model artifact is missing");
            StrategyModel model = StrategyModel.load(in, mapper);
            StrategyModel.StrategyPrediction prediction = model.predict(StrategyFeatures.from(
                    AiTestFixtures.buyerMandate().partyRole(),
                    AiTestFixtures.buyerMandate(),
                    NegotiationPreferences.DEFAULT));
            assertTrue(prediction.confidence() > 0.0 && prediction.confidence() <= 1.0);
            assertEquals(1.0, prediction.probabilities().values().stream()
                    .mapToDouble(Double::doubleValue).sum(), 1e-9);
            assertEquals(5, prediction.probabilities().size());
            assertEquals(StrategyFeatures.NAMES, model.artifact().featureNames());
            assertTrue(model.artifact().dataset().contains("synthetic"));
            assertFalse(model.artifact().metrics().isEmpty());
        }
    }

    @Test
    void rejectsInvalidArtifacts() {
        StrategyModel.Artifact wrongSchema = artifact(
                99, StrategyModel.MODEL_TYPE, StrategyFeatures.NAMES,
                names(LABELS), zeroWeights(), zeroBiases());
        assertThrows(IllegalArgumentException.class, () -> StrategyModel.of(wrongSchema));

        StrategyModel.Artifact wrongType = artifact(
                StrategyModel.SCHEMA_VERSION, "neural_magic", StrategyFeatures.NAMES,
                names(LABELS), zeroWeights(), zeroBiases());
        assertThrows(IllegalArgumentException.class, () -> StrategyModel.of(wrongType));

        StrategyModel.Artifact wrongFeatures = artifact(
                StrategyModel.SCHEMA_VERSION, StrategyModel.MODEL_TYPE, List.of("x"),
                names(LABELS), zeroWeights(), zeroBiases());
        assertThrows(IllegalArgumentException.class, () -> StrategyModel.of(wrongFeatures));

        StrategyModel.Artifact wrongLabels = artifact(
                StrategyModel.SCHEMA_VERSION, StrategyModel.MODEL_TYPE, StrategyFeatures.NAMES,
                List.of("NOT_A_STRATEGY"), new double[][] {new double[12]}, new double[1]);
        assertThrows(IllegalArgumentException.class, () -> StrategyModel.of(wrongLabels));

        StrategyModel.Artifact wrongDimensions = artifact(
                StrategyModel.SCHEMA_VERSION, StrategyModel.MODEL_TYPE, StrategyFeatures.NAMES,
                names(LABELS), new double[][] {new double[12]}, new double[1]);
        assertThrows(IllegalArgumentException.class, () -> StrategyModel.of(wrongDimensions));
    }

    @Test
    void learnedSelectorReportsItsSource() {
        StrategyModel model = zeroModel();
        LearnedStrategySelector selector = new LearnedStrategySelector(model);
        assertTrue(selector.isAvailable());
        StrategySelection selection = selector.select(AiTestFixtures.context(
                AiTestFixtures.buyerMandate().partyRole(),
                AiTestFixtures.buyerMandate(),
                List.of()));
        assertEquals(StrategySelection.Source.LEARNED, selection.source());
        assertTrue(selection.confidence() > 0.0);
    }

    @Test
    void missingArtifactIsFailSafe() {
        LearnedStrategySelector selector = new LearnedStrategySelector(mapper, "ai/missing-model.json");
        assertFalse(selector.isAvailable());
        AiNegotiationException error = assertThrows(AiNegotiationException.class,
                () -> selector.select(AiTestFixtures.context(
                        AiTestFixtures.buyerMandate().partyRole(),
                        AiTestFixtures.buyerMandate(),
                        List.of())));
        assertEquals(AiNegotiationException.Code.INVALID_STATE, error.code());
    }

    @Test
    void hybridUsesRuleBasedWhenLearnedModelIsUnavailableOrBelowThreshold() {
        RuleBasedStrategySelector rule = new RuleBasedStrategySelector();
        HybridStrategySelector unavailable = new HybridStrategySelector(
                rule, new LearnedStrategySelector(mapper, "ai/missing-model.json"), 0.20);
        StrategySelection fallback = unavailable.select(AiTestFixtures.context(
                AiTestFixtures.buyerMandate().partyRole(),
                AiTestFixtures.buyerMandate(),
                List.of()));
        assertEquals(StrategySelection.Source.RULE_BASED, fallback.source());

        LearnedStrategySelector uniform = new LearnedStrategySelector(zeroModel());
        HybridStrategySelector highThreshold = new HybridStrategySelector(rule, uniform, 0.50);
        assertEquals(StrategySelection.Source.RULE_BASED, highThreshold.select(
                AiTestFixtures.context(
                        AiTestFixtures.buyerMandate().partyRole(),
                        AiTestFixtures.buyerMandate(),
                        List.of())).source());

        HybridStrategySelector lowThreshold = new HybridStrategySelector(rule, uniform, 0.20);
        assertEquals(StrategySelection.Source.LEARNED, lowThreshold.select(
                AiTestFixtures.context(
                        AiTestFixtures.buyerMandate().partyRole(),
                        AiTestFixtures.buyerMandate(),
                        List.of())).source());
    }

    private static StrategyModel zeroModel() {
        return StrategyModel.of(artifact(
                StrategyModel.SCHEMA_VERSION,
                StrategyModel.MODEL_TYPE,
                StrategyFeatures.NAMES,
                names(LABELS),
                zeroWeights(),
                zeroBiases()));
    }

    private static StrategyModel.Artifact artifact(
            int schemaVersion,
            String modelType,
            List<String> featureNames,
            List<String> labels,
            double[][] weights,
            double[] biases) {
        return new StrategyModel.Artifact(
                schemaVersion,
                modelType,
                "test",
                1L,
                featureNames,
                labels,
                new double[featureNames.size()],
                filled(featureNames.size(), 1.0),
                weights,
                biases,
                Map.of("trainingSamples", 1),
                Map.of("test", true));
    }

    private static List<String> names(List<NegotiationStrategy> labels) {
        return labels.stream().map(Enum::name).toList();
    }

    private static double[][] zeroWeights() {
        double[][] weights = new double[LABELS.size()][StrategyFeatures.NAMES.size()];
        return weights;
    }

    private static double[] zeroBiases() {
        return new double[LABELS.size()];
    }

    private static double[] filled(int length, double value) {
        double[] values = new double[length];
        java.util.Arrays.fill(values, value);
        return values;
    }
}
