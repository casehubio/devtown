package io.casehub.devtown.domain.cbr;

import java.util.HashMap;
import java.util.Map;

public final class CbrWeightAdjuster {

    private CbrWeightAdjuster() {}

    public static Map<String, Double> adjust(Map<String, Double> currentWeights,
                                              Map<String, Double> similarityBreakdown,
                                              boolean predictionCorrect,
                                              double learningRate) {
        var adjusted = new HashMap<>(currentWeights);
        for (var entry : similarityBreakdown.entrySet()) {
            String dimension = entry.getKey();
            double contribution = entry.getValue();
            Double weight = adjusted.get(dimension);
            if (weight == null || contribution == 0.0) continue;

            double delta = learningRate * contribution;
            if (predictionCorrect) {
                adjusted.put(dimension, weight + delta);
            } else {
                adjusted.put(dimension, Math.max(0.01, weight - delta));
            }
        }
        return Map.copyOf(adjusted);
    }
}
