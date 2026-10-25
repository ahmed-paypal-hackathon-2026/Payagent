package com.payagent.ai.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.ai.negotiation.NegotiationStrategy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small multinomial logistic-regression model that selects a negotiation
 * archetype from structured features.
 *
 * <p>The model is intentionally tiny (12 features, 5 classes) so it can run
 * in-process with microsecond latency, no GPU, no model server and no network
 * dependency. It never emits prices.</p>
 */
public final class StrategyModel {

    public static final int SCHEMA_VERSION = 1;
    public static final String MODEL_TYPE = "multinomial_logistic_regression";

    private final Artifact artifact;
    private final NegotiationStrategy[] labels;
    private final int featureCount;

    private StrategyModel(Artifact artifact) {
        this.artifact = artifact;
        validate(artifact);
        this.featureCount = artifact.featureNames().size();
        this.labels = artifact.labels().stream()
                .map(NegotiationStrategy::valueOf)
                .toArray(NegotiationStrategy[]::new);
    }

    public static StrategyModel of(Artifact artifact) {
        return new StrategyModel(artifact);
    }

    public static StrategyModel load(Path path, ObjectMapper mapper) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return load(in, mapper);
        }
    }

    public static StrategyModel load(InputStream in, ObjectMapper mapper) throws IOException {
        return new StrategyModel(mapper.readValue(in, Artifact.class));
    }

    public void write(Path path, ObjectMapper mapper) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), artifact);
    }

    public Artifact artifact() {
        return artifact;
    }

    public StrategyPrediction predict(StrategyFeatures features) {
        double[] x = features.toVector();
        if (x.length != featureCount) {
            throw new IllegalArgumentException("feature count mismatch");
        }
        for (double value : x) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("feature values must be finite");
            }
        }

        double[] logits = new double[labels.length];
        double max = Double.NEGATIVE_INFINITY;
        for (int c = 0; c < labels.length; c++) {
            double z = artifact.biases()[c];
            for (int j = 0; j < featureCount; j++) {
                double std = artifact.std()[j] == 0.0 ? 1.0 : artifact.std()[j];
                double normalized = (x[j] - artifact.mean()[j]) / std;
                z += artifact.weights()[c][j] * normalized;
            }
            logits[c] = z;
            max = Math.max(max, z);
        }

        double sum = 0.0;
        Map<NegotiationStrategy, Double> probabilities = new EnumMap<>(NegotiationStrategy.class);
        for (int c = 0; c < labels.length; c++) {
            double value = Math.exp(logits[c] - max);
            probabilities.put(labels[c], value);
            sum += value;
        }
        double bestProbability = -1.0;
        NegotiationStrategy best = labels[0];
        for (Map.Entry<NegotiationStrategy, Double> entry : probabilities.entrySet()) {
            double probability = entry.getValue() / sum;
            probabilities.put(entry.getKey(), probability);
            if (probability > bestProbability) {
                bestProbability = probability;
                best = entry.getKey();
            }
        }
        return new StrategyPrediction(best, bestProbability, Map.copyOf(probabilities));
    }

    private static void validate(Artifact artifact) {
        if (artifact == null) {
            throw new IllegalArgumentException("model artifact is missing");
        }
        if (artifact.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported model schema version");
        }
        if (!MODEL_TYPE.equals(artifact.modelType())) {
            throw new IllegalArgumentException("unsupported model type");
        }
        if (artifact.featureNames() == null
                || !artifact.featureNames().equals(StrategyFeatures.NAMES)) {
            throw new IllegalArgumentException("model feature names do not match the implementation");
        }
        if (artifact.labels() == null || artifact.labels().isEmpty()) {
            throw new IllegalArgumentException("model labels are missing");
        }
        int classes = artifact.labels().size();
        int features = artifact.featureNames().size();
        if (artifact.mean() == null || artifact.mean().length != features
                || artifact.std() == null || artifact.std().length != features) {
            throw new IllegalArgumentException("model normalization vectors are invalid");
        }
        if (artifact.weights() == null || artifact.weights().length != classes
                || artifact.biases() == null || artifact.biases().length != classes) {
            throw new IllegalArgumentException("model parameter dimensions are invalid");
        }
        for (double[] row : artifact.weights()) {
            if (row == null || row.length != features) {
                throw new IllegalArgumentException("model weight rows are invalid");
            }
            for (double value : row) {
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("model weights must be finite");
                }
            }
        }
        for (double value : artifact.biases()) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("model biases must be finite");
            }
        }
        for (String label : artifact.labels()) {
            try {
                NegotiationStrategy.valueOf(label);
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("unknown strategy label in model: " + label);
            }
        }
    }

    /** JSON-friendly model artifact. Keep field names stable; they are versioned. */
    public record Artifact(
            int schemaVersion,
            String modelType,
            String dataset,
            long trainingSeed,
            List<String> featureNames,
            List<String> labels,
            double[] mean,
            double[] std,
            double[][] weights,
            double[] biases,
            Map<String, Object> metrics,
            Map<String, Object> hyperparameters) {

        public Artifact {
            featureNames = featureNames == null ? null : List.copyOf(featureNames);
            labels = labels == null ? null : List.copyOf(labels);
            metrics = metrics == null ? Map.of() : new LinkedHashMap<>(metrics);
            hyperparameters = hyperparameters == null ? Map.of() : new LinkedHashMap<>(hyperparameters);
        }
    }

    public record StrategyPrediction(
            NegotiationStrategy strategy,
            double confidence,
            Map<NegotiationStrategy, Double> probabilities) {}

    /** Builder used by the training pipeline to keep artifact construction explicit. */
    public static final class Builder {
        private int schemaVersion = SCHEMA_VERSION;
        private String modelType = MODEL_TYPE;
        private String dataset;
        private long trainingSeed;
        private List<String> featureNames = new ArrayList<>(StrategyFeatures.NAMES);
        private List<String> labels = new ArrayList<>();
        private double[] mean;
        private double[] std;
        private double[][] weights;
        private double[] biases;
        private Map<String, Object> metrics = new LinkedHashMap<>();
        private Map<String, Object> hyperparameters = new LinkedHashMap<>();

        public Builder dataset(String dataset) {
            this.dataset = dataset;
            return this;
        }

        public Builder trainingSeed(long trainingSeed) {
            this.trainingSeed = trainingSeed;
            return this;
        }

        public Builder labels(List<NegotiationStrategy> labels) {
            this.labels = labels.stream().map(Enum::name).toList();
            return this;
        }

        public Builder mean(double[] mean) {
            this.mean = mean;
            return this;
        }

        public Builder std(double[] std) {
            this.std = std;
            return this;
        }

        public Builder weights(double[][] weights) {
            this.weights = weights;
            return this;
        }

        public Builder biases(double[] biases) {
            this.biases = biases;
            return this;
        }

        public Builder metrics(Map<String, Object> metrics) {
            this.metrics = new LinkedHashMap<>(metrics);
            return this;
        }

        public Builder hyperparameters(Map<String, Object> hyperparameters) {
            this.hyperparameters = new LinkedHashMap<>(hyperparameters);
            return this;
        }

        public StrategyModel build() {
            return new StrategyModel(new Artifact(
                    schemaVersion, modelType, dataset, trainingSeed, featureNames, labels,
                    mean, std, weights, biases, metrics, hyperparameters));
        }
    }
}
