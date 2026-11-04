package com.payagent.ai.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.ai.AiNegotiationException;
import com.payagent.ai.negotiation.NegotiationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Optional;

/**
 * Loads the trained strategy artifact from the classpath. Loading is fail-safe:
 * a missing or corrupt artifact never prevents the application from starting;
 * callers fall back to the rule-based selector.
 */
@Component
public class LearnedStrategySelector implements StrategySelector {

    private static final Logger log = LoggerFactory.getLogger(LearnedStrategySelector.class);

    private final StrategyModel model;

    @Autowired
    public LearnedStrategySelector(
            ObjectMapper mapper,
            @Value("${payagent.ai.strategy-model:ai/strategy-selector-v1.json}") String resource) {
        this(load(mapper, resource));
    }

    public LearnedStrategySelector(StrategyModel model) {
        this.model = model;
    }

    @Override
    public StrategySelection select(NegotiationContext context) {
        if (model == null) {
            throw new AiNegotiationException(
                    AiNegotiationException.Code.INVALID_STATE,
                    "learned strategy model is not available");
        }
        StrategyModel.StrategyPrediction prediction =
                model.predict(StrategyFeatures.from(context));
        return new StrategySelection(
                prediction.strategy(),
                prediction.confidence(),
                StrategySelection.Source.LEARNED,
                prediction.probabilities(),
                "trained synthetic-v1 multinomial logistic regression");
    }

    public boolean isAvailable() {
        return model != null;
    }

    public Optional<StrategyModel> model() {
        return Optional.ofNullable(model);
    }

    private static StrategyModel load(ObjectMapper mapper, String resource) {
        if (resource == null || resource.isBlank()) {
            return null;
        }
        String path = resource.startsWith("classpath:") ? resource.substring("classpath:".length()) : resource;
        ClassPathResource classPathResource = new ClassPathResource(path);
        if (!classPathResource.exists()) {
            log.warn("AI strategy model {} not found; using rule-based selector", path);
            return null;
        }
        try (InputStream in = classPathResource.getInputStream()) {
            StrategyModel loaded = StrategyModel.load(in, mapper);
            log.info("Loaded AI strategy model {} ({} samples, dataset {})",
                    path,
                    loaded.artifact().metrics().getOrDefault("trainingSamples", "unknown"),
                    loaded.artifact().dataset());
            return loaded;
        } catch (Exception ex) {
            log.warn("AI strategy model {} could not be loaded ({}); using rule-based selector",
                    path, ex.getClass().getSimpleName());
            return null;
        }
    }
}
