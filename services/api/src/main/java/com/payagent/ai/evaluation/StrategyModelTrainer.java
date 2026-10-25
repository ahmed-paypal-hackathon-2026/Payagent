package com.payagent.ai.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.ai.negotiation.NegotiationStrategy;
import com.payagent.ai.strategy.RuleBasedStrategySelector;
import com.payagent.ai.strategy.StrategyFeatures;
import com.payagent.ai.strategy.StrategyModel;
import com.payagent.ai.strategy.StrategySelection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline training and evaluation entry point for the strategy selector.
 *
 * <p>Run it with the command documented in {@code docs/AI_BENCHMARK.md}. The
 * generated artifact is small JSON and is safe to commit; scenario data is
 * deterministic and generated from the recorded seed, so no large dataset or
 * binary model needs to be stored in Git.</p>
 *
 * <p>Labels are soft targets derived from simulated utility, not human labels
 * and not the rule-based selector's output. This avoids simply distilling the
 * baseline into a more complex model.</p>
 */
public final class StrategyModelTrainer {

    private static final List<NegotiationStrategy> LABELS = List.of(
            NegotiationStrategy.PATIENT_PRICE,
            NegotiationStrategy.FIRM,
            NegotiationStrategy.BALANCED,
            NegotiationStrategy.FLEXIBLE,
            NegotiationStrategy.FAST_SALE);

    private StrategyModelTrainer() {}

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        System.out.printf(Locale.ROOT,
                "Generating synthetic scenarios: train=%d eval=%d seed=%d%n",
                options.trainCount(), options.evalCount(), options.seed());
        long start = System.nanoTime();
        SyntheticScenarioGenerator generator = new SyntheticScenarioGenerator();
        List<SyntheticScenario> trainScenarios = generator.generate(options.trainCount(), options.seed());
        List<SyntheticScenario> evalScenarios = generator.generate(options.evalCount(), options.evalSeed());

        Dataset train = simulateDataset(trainScenarios, options.softTargetTemperature());
        Dataset eval = simulateDataset(evalScenarios, options.softTargetTemperature());
        TrainedModel trained = train(train, options.epochs(), options.learningRate(), options.l2());
        StrategyModel model = trained.toModel(options, train);

        Evaluation evaluation = evaluate(model, eval);
        Map<String, Object> metrics = new LinkedHashMap<>(evaluation.metrics());
        metrics.put("trainingSamples", train.size());
        metrics.put("evalSamples", eval.size());
        metrics.put("trainingLoss", trained.finalLoss());
        metrics.put("pipelineMillis", (System.nanoTime() - start) / 1_000_000L);

        Map<String, Object> hyperparameters = new LinkedHashMap<>();
        hyperparameters.put("optimizer", "full-batch gradient descent");
        hyperparameters.put("epochs", options.epochs());
        hyperparameters.put("learningRate", options.learningRate());
        hyperparameters.put("l2", options.l2());
        hyperparameters.put("softTargetTemperature", options.softTargetTemperature());
        hyperparameters.put("evalSeed", options.evalSeed());
        hyperparameters.put("featureSchemaVersion", StrategyFeatures.SCHEMA_VERSION);
        hyperparameters.put("labelOrder", LABELS.stream().map(Enum::name).toList());

        Map<String, Object> artifactMetrics = new LinkedHashMap<>(metrics);
        artifactMetrics.remove("predictionLatencyMicros");
        artifactMetrics.remove("pipelineMillis");

        StrategyModel.Artifact artifact = new StrategyModel.Artifact(
                StrategyModel.SCHEMA_VERSION,
                StrategyModel.MODEL_TYPE,
                SyntheticScenarioGenerator.DATASET_NAME,
                options.seed(),
                StrategyFeatures.NAMES,
                LABELS.stream().map(Enum::name).toList(),
                trained.mean(),
                trained.std(),
                trained.weights(),
                trained.biases(),
                artifactMetrics,
                hyperparameters);
        StrategyModel trainedModel = StrategyModel.of(artifact);

        ObjectMapper mapper = new ObjectMapper();
        trainedModel.write(options.output(), mapper);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("dataset", SyntheticScenarioGenerator.DATASET_NAME);
        report.put("seed", options.seed());
        report.put("evalSeed", options.evalSeed());
        report.put("trainScenarios", options.trainCount());
        report.put("evalScenarios", options.evalCount());
        report.put("metrics", metrics);
        report.put("hyperparameters", hyperparameters);
        Path reportPath = options.report();
        if (reportPath != null) {
            Files.createDirectories(reportPath.toAbsolutePath().getParent());
            Files.writeString(reportPath, mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(report));
        }

        System.out.println("Artifact written to " + options.output().toAbsolutePath());
        if (reportPath != null) {
            System.out.println("Report written to " + reportPath.toAbsolutePath());
        }
        printSummary(metrics);
    }

    private static Dataset simulateDataset(
            List<SyntheticScenario> scenarios,
            double temperature) {
        NegotiationSimulator simulator = new NegotiationSimulator();
        List<double[]> features = new ArrayList<>();
        List<double[]> targets = new ArrayList<>();
        List<double[]> utilities = new ArrayList<>();
        List<List<SimulationResult>> results = new ArrayList<>();
        for (SyntheticScenario scenario : scenarios) {
            StrategyFeatures feature = StrategyFeatures.from(
                    scenario.callerRole(), scenario.mandate(), scenario.preferences());
            features.add(feature.toVector());

            double[] scenarioUtilities = new double[LABELS.size()];
            List<SimulationResult> scenarioResults = new ArrayList<>();
            for (int i = 0; i < LABELS.size(); i++) {
                SimulationResult result = simulator.simulate(scenario, LABELS.get(i));
                scenarioResults.add(result);
                scenarioUtilities[i] = result.utility();
            }
            utilities.add(scenarioUtilities);
            results.add(List.copyOf(scenarioResults));
            targets.add(softTargets(scenarioUtilities, temperature));
        }
        return new Dataset(features, targets, utilities, results, scenarios);
    }

    private static double[] softTargets(double[] utilities, double temperature) {
        double max = Double.NEGATIVE_INFINITY;
        for (double utility : utilities) {
            max = Math.max(max, utility);
        }
        if (max <= 0.0) {
            double uniform = 1.0 / utilities.length;
            double[] targets = new double[utilities.length];
            java.util.Arrays.fill(targets, uniform);
            return targets;
        }
        double[] values = new double[utilities.length];
        double sum = 0.0;
        for (int i = 0; i < utilities.length; i++) {
            values[i] = Math.exp((utilities[i] - max) / Math.max(1e-6, temperature));
            sum += values[i];
        }
        for (int i = 0; i < values.length; i++) {
            values[i] /= sum;
        }
        return values;
    }

    private static TrainedModel train(
            Dataset dataset,
            int epochs,
            double learningRate,
            double l2) {
        int samples = dataset.size();
        int featureCount = StrategyFeatures.NAMES.size();
        int classes = LABELS.size();
        double[] mean = new double[featureCount];
        double[] std = new double[featureCount];
        for (double[] row : dataset.features()) {
            for (int j = 0; j < featureCount; j++) {
                mean[j] += row[j];
            }
        }
        for (int j = 0; j < featureCount; j++) {
            mean[j] /= samples;
        }
        for (double[] row : dataset.features()) {
            for (int j = 0; j < featureCount; j++) {
                double diff = row[j] - mean[j];
                std[j] += diff * diff;
            }
        }
        for (int j = 0; j < featureCount; j++) {
            std[j] = Math.sqrt(std[j] / samples);
            if (std[j] < 1e-9) {
                std[j] = 1.0;
            }
        }

        double[][] x = new double[samples][featureCount];
        for (int i = 0; i < samples; i++) {
            for (int j = 0; j < featureCount; j++) {
                x[i][j] = (dataset.features().get(i)[j] - mean[j]) / std[j];
            }
        }

        double[][] weights = new double[classes][featureCount];
        double[] biases = new double[classes];
        double loss = 0.0;
        for (int epoch = 0; epoch < epochs; epoch++) {
            double[][] gradW = new double[classes][featureCount];
            double[] gradB = new double[classes];
            loss = 0.0;
            for (int i = 0; i < samples; i++) {
                double[] probabilities = softmax(logits(x[i], weights, biases));
                for (int c = 0; c < classes; c++) {
                    double target = dataset.targets().get(i)[c];
                    double diff = probabilities[c] - target;
                    loss -= target * Math.log(Math.max(1e-12, probabilities[c]));
                    gradB[c] += diff;
                    for (int j = 0; j < featureCount; j++) {
                        gradW[c][j] += diff * x[i][j];
                    }
                }
            }
            loss /= samples;
            for (int c = 0; c < classes; c++) {
                biases[c] -= learningRate * gradB[c] / samples;
                for (int j = 0; j < featureCount; j++) {
                    weights[c][j] -= learningRate * (gradW[c][j] / samples + l2 * weights[c][j]);
                }
            }
            if (epoch == 0 || (epoch + 1) % 100 == 0 || epoch == epochs - 1) {
                System.out.printf(Locale.ROOT, "epoch %d/%d loss=%.5f%n",
                        epoch + 1, epochs, loss);
            }
        }
        return new TrainedModel(mean, std, weights, biases, loss);
    }

    private static Evaluation evaluate(StrategyModel model, Dataset dataset) {
        RuleBasedStrategySelector ruleBased = new RuleBasedStrategySelector();
        int samples = dataset.size();
        double learnedPolicy = 0.0;
        double learnedExpected = 0.0;
        double rulePolicy = 0.0;
        double oracle = 0.0;
        double[] fixed = new double[LABELS.size()];
        int learnedAgreements = 0;
        int ruleAgreements = 0;
        int[] fixedAgreements = new int[LABELS.size()];
        int hardCorrect = 0;
        int[] selected = new int[LABELS.size()];
        double regret = 0.0;
        double confidenceSum = 0.0;
        double[] hybridPolicyByThreshold = new double[7];
        int[] hybridUseByThreshold = new int[7];
        double[] thresholds = {0.00, 0.20, 0.25, 0.30, 0.35, 0.40, 0.50};
        long latencyNanos = 0L;

        for (int i = 0; i < 1000; i++) {
            SyntheticScenario scenario = dataset.scenarios().get(i % samples);
            model.predict(StrategyFeatures.from(
                    scenario.callerRole(), scenario.mandate(), scenario.preferences()));
        }

        for (int i = 0; i < samples; i++) {
            SyntheticScenario scenario = dataset.scenarios().get(i);
            double[] utilities = dataset.utilities().get(i);
            List<SimulationResult> results = dataset.results().get(i);
            StrategyFeatures features = StrategyFeatures.from(
                    scenario.callerRole(), scenario.mandate(), scenario.preferences());

            long start = System.nanoTime();
            StrategyModel.StrategyPrediction prediction = model.predict(features);
            latencyNanos += System.nanoTime() - start;

            int learnedIndex = LABELS.indexOf(prediction.strategy());
            selected[learnedIndex]++;
            learnedPolicy += utilities[learnedIndex];
            double expected = 0.0;
            for (int c = 0; c < LABELS.size(); c++) {
                expected += prediction.probabilities().getOrDefault(LABELS.get(c), 0.0) * utilities[c];
            }
            learnedExpected += expected;
            confidenceSum += prediction.confidence();
            if (results.get(learnedIndex).dealReached()) {
                learnedAgreements++;
            }

            StrategySelection rule = ruleBased.select(scenario.context(List.of()));
            int ruleIndex = LABELS.indexOf(rule.strategy());
            rulePolicy += utilities[ruleIndex];
            for (int t = 0; t < thresholds.length; t++) {
                if (prediction.confidence() >= thresholds[t]) {
                    hybridPolicyByThreshold[t] += utilities[learnedIndex];
                    hybridUseByThreshold[t]++;
                } else {
                    hybridPolicyByThreshold[t] += utilities[ruleIndex];
                }
            }
            if (results.get(ruleIndex).dealReached()) {
                ruleAgreements++;
            }

            double best = utilities[0];
            for (double utility : utilities) {
                best = Math.max(best, utility);
            }
            oracle += best;
            regret += best - utilities[learnedIndex];
            if (utilities[learnedIndex] >= best - 1e-9) {
                hardCorrect++;
            }
            for (int c = 0; c < LABELS.size(); c++) {
                fixed[c] += utilities[c];
                if (results.get(c).dealReached()) {
                    fixedAgreements[c]++;
                }
            }
        }

        int bestFixedIndex = 0;
        for (int c = 1; c < LABELS.size(); c++) {
            if (fixed[c] > fixed[bestFixedIndex]) {
                bestFixedIndex = c;
            }
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("hardBestAccuracy", (double) hardCorrect / samples);
        metrics.put("learnedPolicyUtility", learnedPolicy / samples);
        metrics.put("learnedExpectedUtility", learnedExpected / samples);
        metrics.put("ruleBasedPolicyUtility", rulePolicy / samples);
        metrics.put("oracleUtility", oracle / samples);
        metrics.put("meanRegretVsOracle", regret / samples);
        metrics.put("improvementOverRuleBased", (learnedPolicy - rulePolicy) / samples);
        metrics.put("learnedAgreementRate", (double) learnedAgreements / samples);
        metrics.put("ruleBasedAgreementRate", (double) ruleAgreements / samples);
        metrics.put("bestFixedStrategy", LABELS.get(bestFixedIndex).name());
        metrics.put("bestFixedPolicyUtility", fixed[bestFixedIndex] / samples);
        metrics.put("predictionLatencyMicros", (double) latencyNanos / samples / 1_000.0);
        metrics.put("meanConfidence", confidenceSum / samples);
        Map<String, Object> hybridMap = new LinkedHashMap<>();
        for (int t = 0; t < thresholds.length; t++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("threshold", thresholds[t]);
            entry.put("utility", hybridPolicyByThreshold[t] / samples);
            entry.put("learnedUseRate", (double) hybridUseByThreshold[t] / samples);
            hybridMap.put(String.format(Locale.ROOT, "%.2f", thresholds[t]), entry);
        }
        metrics.put("hybridThresholdSweep", hybridMap);
        Map<String, Object> fixedMap = new LinkedHashMap<>();
        Map<String, Object> fixedAgreementMap = new LinkedHashMap<>();
        for (int c = 0; c < LABELS.size(); c++) {
            fixedMap.put(LABELS.get(c).name(), fixed[c] / samples);
            fixedAgreementMap.put(LABELS.get(c).name(), (double) fixedAgreements[c] / samples);
        }
        metrics.put("fixedStrategyUtility", fixedMap);
        metrics.put("fixedStrategyAgreementRate", fixedAgreementMap);
        Map<String, Object> selectionByName = new LinkedHashMap<>();
        for (int c = 0; c < LABELS.size(); c++) {
            selectionByName.put(LABELS.get(c).name(), selected[c]);
        }
        metrics.put("selectionCounts", selectionByName);
        return new Evaluation(metrics);
    }

    private static double[] logits(double[] x, double[][] weights, double[] biases) {
        double[] logits = new double[weights.length];
        for (int c = 0; c < weights.length; c++) {
            double value = biases[c];
            for (int j = 0; j < x.length; j++) {
                value += weights[c][j] * x[j];
            }
            logits[c] = value;
        }
        return logits;
    }

    private static double[] softmax(double[] logits) {
        double max = Double.NEGATIVE_INFINITY;
        for (double value : logits) {
            max = Math.max(max, value);
        }
        double[] probabilities = new double[logits.length];
        double sum = 0.0;
        for (int i = 0; i < logits.length; i++) {
            probabilities[i] = Math.exp(logits[i] - max);
            sum += probabilities[i];
        }
        for (int i = 0; i < probabilities.length; i++) {
            probabilities[i] /= sum;
        }
        return probabilities;
    }

    private static void printSummary(Map<String, Object> metrics) {
        System.out.printf(Locale.ROOT,
                "hardBestAccuracy=%.4f learnedPolicy=%.4f learnedExpected=%.4f "
                        + "ruleBased=%.4f oracle=%.4f regret=%.4f improvement=%.4f%n",
                metric(metrics, "hardBestAccuracy"),
                metric(metrics, "learnedPolicyUtility"),
                metric(metrics, "learnedExpectedUtility"),
                metric(metrics, "ruleBasedPolicyUtility"),
                metric(metrics, "oracleUtility"),
                metric(metrics, "meanRegretVsOracle"),
                metric(metrics, "improvementOverRuleBased"));
        System.out.printf(Locale.ROOT,
                "agreement learned=%.4f ruleBased=%.4f bestFixed=%s (%.4f) latency=%.2fus%n",
                metric(metrics, "learnedAgreementRate"),
                metric(metrics, "ruleBasedAgreementRate"),
                metrics.get("bestFixedStrategy"),
                metric(metrics, "bestFixedPolicyUtility"),
                metric(metrics, "predictionLatencyMicros"));
    }

    private static double metric(Map<String, Object> metrics, String key) {
        Object value = metrics.get(key);
        return value instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    private record Dataset(
            List<double[]> features,
            List<double[]> targets,
            List<double[]> utilities,
            List<List<SimulationResult>> results,
            List<SyntheticScenario> scenarios) {
        int size() {
            return features.size();
        }
    }

    private record TrainedModel(
            double[] mean,
            double[] std,
            double[][] weights,
            double[] biases,
            double finalLoss) {
        StrategyModel toModel(Options options, Dataset dataset) {
            StrategyModel.Artifact artifact = new StrategyModel.Artifact(
                    StrategyModel.SCHEMA_VERSION,
                    StrategyModel.MODEL_TYPE,
                    SyntheticScenarioGenerator.DATASET_NAME,
                    options.seed(),
                    StrategyFeatures.NAMES,
                    LABELS.stream().map(Enum::name).toList(),
                    mean,
                    std,
                    weights,
                    biases,
                    Map.of("trainingSamples", dataset.size()),
                    Map.of(
                            "epochs", options.epochs(),
                            "learningRate", options.learningRate(),
                            "l2", options.l2(),
                            "softTargetTemperature", options.softTargetTemperature()));
            return StrategyModel.of(artifact);
        }
    }

    private record Evaluation(Map<String, Object> metrics) {}

    record Options(
            int trainCount,
            int evalCount,
            long seed,
            long evalSeed,
            int epochs,
            double learningRate,
            double l2,
            double softTargetTemperature,
            Path output,
            Path report) {

        static Options parse(String[] args) {
            int train = 3000;
            int eval = 800;
            long seed = 20261025L;
            long evalSeed = seed + 1;
            int epochs = 600;
            double learningRate = 0.15;
            double l2 = 0.0005;
            double temperature = 0.08;
            Path output = Path.of("src/main/resources/ai/strategy-selector-v1.json");
            Path report = Path.of("target/ai-benchmark/report.json");
            for (int i = 0; i < args.length; i++) {
                String key = args[i];
                String value = i + 1 < args.length ? args[i + 1] : null;
                switch (key) {
                    case "--train" -> train = Integer.parseInt(require(value, key));
                    case "--eval" -> eval = Integer.parseInt(require(value, key));
                    case "--seed" -> seed = Long.parseLong(require(value, key));
                    case "--eval-seed" -> evalSeed = Long.parseLong(require(value, key));
                    case "--epochs" -> epochs = Integer.parseInt(require(value, key));
                    case "--learning-rate" -> learningRate = Double.parseDouble(require(value, key));
                    case "--l2" -> l2 = Double.parseDouble(require(value, key));
                    case "--temperature" -> temperature = Double.parseDouble(require(value, key));
                    case "--out" -> output = Path.of(require(value, key));
                    case "--report" -> report = Path.of(require(value, key));
                    default -> throw new IllegalArgumentException("unknown option: " + key);
                }
                i++;
            }
            if (train < 50 || eval < 20 || epochs < 1) {
                throw new IllegalArgumentException("counts/epochs are too small");
            }
            if (learningRate <= 0.0 || l2 < 0.0 || temperature <= 0.0) {
                throw new IllegalArgumentException("hyperparameters must be positive");
            }
            return new Options(train, eval, seed, evalSeed, epochs, learningRate, l2, temperature, output, report);
        }

        private static String require(String value, String key) {
            if (value == null || value.startsWith("--")) {
                throw new IllegalArgumentException("missing value for " + key);
            }
            return value;
        }
    }
}
