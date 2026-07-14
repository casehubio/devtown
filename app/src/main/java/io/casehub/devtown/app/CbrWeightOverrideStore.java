package io.casehub.devtown.app;

import io.casehub.devtown.domain.cbr.CbrWeightAdjuster;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class CbrWeightOverrideStore {

    private static final Logger LOG = Logger.getLogger(CbrWeightOverrideStore.class);
    private static final int DEFAULT_MIN_SAMPLES = 10;
    private static final double DEFAULT_LEARNING_RATE = 0.1;

    private final ConcurrentHashMap<String, Double> overrides = new ConcurrentHashMap<>();
    private final AtomicInteger sampleCount = new AtomicInteger(0);
    private final int minSamples;
    private final double learningRate;

    public CbrWeightOverrideStore() {
        this(DEFAULT_MIN_SAMPLES, DEFAULT_LEARNING_RATE);
    }

    CbrWeightOverrideStore(int minSamples, double learningRate) {
        this.minSamples = minSamples;
        this.learningRate = learningRate;
    }

    public void recordOutcome(Map<String, Double> currentWeights,
                              Map<String, Double> similarityBreakdown,
                              boolean predictionCorrect) {
        int count = sampleCount.incrementAndGet();
        if (count < minSamples) {
            LOG.debugf("CBR weight adjustment deferred — %d/%d samples collected", count, minSamples);
            return;
        }

        Map<String, Double> adjusted = CbrWeightAdjuster.adjust(
                currentWeights, similarityBreakdown, predictionCorrect, learningRate);
        overrides.putAll(adjusted);
        LOG.infof("CBR weights adjusted (sample %d, correct=%s): %s", count, predictionCorrect, adjusted);
    }

    public double resolveWeight(String dimension, double preferenceDefault) {
        return overrides.getOrDefault(dimension, preferenceDefault);
    }

    public Map<String, Double> currentOverrides() {
        return Map.copyOf(overrides);
    }

    public int sampleCount() {
        return sampleCount.get();
    }
}
