package io.casehub.devtown.domain.evolution;

import java.util.Map;

public record EvolutionOutcomeRecord(
    String category,
    double healthBefore,
    double healthAfter,
    Map<String, Double> adjustments) {}
