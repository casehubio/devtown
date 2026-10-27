package io.casehub.devtown.domain.evolution;

public record AdjustmentBounds(
    double floor,
    double ceiling,
    double minStep,
    double maxStep,
    double stepFactor,
    Direction direction) {

  public enum Direction {
    INCREASE_ON_LOW_HEALTH,
    DECREASE_ON_LOW_HEALTH
  }
}
