package io.casehub.devtown.domain.evolution;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class CbrInformedAdjustmentStrategy implements AdjustmentStrategy {

  private final ProportionalAdjustmentStrategy fallback;
  private final OutcomeQuery outcomeQuery;
  private final int minPrecedents;
  private final double healthGapTolerance;

  public CbrInformedAdjustmentStrategy(
      ProportionalAdjustmentStrategy fallback,
      OutcomeQuery outcomeQuery,
      int minPrecedents,
      double healthGapTolerance) {
    this.fallback = fallback;
    this.outcomeQuery = outcomeQuery;
    this.minPrecedents = minPrecedents;
    this.healthGapTolerance = healthGapTolerance;
  }

  @Override
  public AdjustmentResult compute(
      String parameter, double currentValue, double healthScore,
      double targetHealth, AdjustmentBounds bounds, String category,
      UUID caseId) {

    double gap = targetHealth - healthScore;
    if (gap <= 0) {
      return fallback.compute(parameter, currentValue, healthScore,
          targetHealth, bounds, category, caseId);
    }

    List<EvolutionOutcomeRecord> precedents = outcomeQuery.findByCategory(category, 50);

    List<EvolutionOutcomeRecord> similar = precedents.stream()
        .filter(p -> Math.abs((targetHealth - p.healthBefore()) - gap) <= healthGapTolerance)
        .filter(p -> !Double.isNaN(p.healthAfter()))
        .filter(p -> p.adjustments().containsKey(parameter))
        .toList();

    if (similar.size() < minPrecedents) {
      return fallback.compute(parameter, currentValue, healthScore,
          targetHealth, bounds, category, caseId);
    }

    EvolutionOutcomeRecord best = similar.stream()
        .max(Comparator.comparingDouble(p -> p.healthAfter() - p.healthBefore()))
        .orElseThrow();

    double cbrValue = best.adjustments().get(parameter);

    cbrValue = Math.max(cbrValue, bounds.floor());
    cbrValue = Math.min(cbrValue, bounds.ceiling());

    double delta = Math.abs(cbrValue - currentValue);
    if (delta > bounds.maxStep()) {
      if (bounds.direction() == AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH) {
        cbrValue = currentValue + bounds.maxStep();
      } else {
        cbrValue = currentValue - bounds.maxStep();
      }
      cbrValue = Math.max(cbrValue, bounds.floor());
      cbrValue = Math.min(cbrValue, bounds.ceiling());
    }

    return new AdjustmentResult(parameter, currentValue, cbrValue,
        "cbr-informed", similar.size(),
        String.format("CBR from %d precedents (best: health %.2f->%.2f), bounded [%.2f, %.2f]",
            similar.size(), best.healthBefore(), best.healthAfter(),
            bounds.floor(), bounds.ceiling()));
  }
}
