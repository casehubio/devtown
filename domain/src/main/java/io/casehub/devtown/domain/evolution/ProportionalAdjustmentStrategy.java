package io.casehub.devtown.domain.evolution;

import java.util.UUID;

public class ProportionalAdjustmentStrategy implements AdjustmentStrategy {

  @Override
  public AdjustmentResult compute(
      String parameter, double currentValue, double healthScore,
      double targetHealth, AdjustmentBounds bounds, String category,
      UUID caseId) {

    double gap = targetHealth - healthScore;
    if (gap <= 0) {
      return new AdjustmentResult(parameter, currentValue, currentValue,
          "proportional", 0,
          String.format("Health %.2f >= target %.2f, no adjustment needed",
              healthScore, targetHealth));
    }

    double rawDelta = gap * bounds.stepFactor();

    double absDelta = Math.abs(rawDelta);
    absDelta = Math.max(absDelta, bounds.minStep());
    absDelta = Math.min(absDelta, bounds.maxStep());

    double newValue;
    if (bounds.direction() == AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH) {
      newValue = currentValue + absDelta;
    } else {
      newValue = currentValue - absDelta;
    }

    newValue = Math.max(newValue, bounds.floor());
    newValue = Math.min(newValue, bounds.ceiling());

    return new AdjustmentResult(parameter, currentValue, newValue,
        "proportional", 0,
        String.format("Health %.2f, gap %.2f, step %+.4f (bounded [%.2f, %.2f])",
            healthScore, gap, newValue - currentValue, bounds.floor(), bounds.ceiling()));
  }
}
