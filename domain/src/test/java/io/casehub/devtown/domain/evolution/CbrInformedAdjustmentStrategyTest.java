package io.casehub.devtown.domain.evolution;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CbrInformedAdjustmentStrategyTest {

  private final UUID caseId = UUID.randomUUID();

  @Test
  void fallsBackToProportionalWhenNoPrecedents() {
    OutcomeQuery emptyQuery = (category, limit) -> List.of();
    var strategy = new CbrInformedAdjustmentStrategy(
        new ProportionalAdjustmentStrategy(), emptyQuery, 5, 0.1);

    var bounds = new AdjustmentBounds(0.30, 0.95, 0.01, 0.10, 1.0,
        AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
    var result = strategy.compute("threshold", 0.70, 0.45, 0.6, bounds,
        "reviewer-calibration", caseId);

    assertEquals("proportional", result.method());
    assertEquals(0, result.precedentCount());
  }

  @Test
  void fallsBackWhenBelowMinPrecedents() {
    OutcomeQuery fewResults = (category, limit) -> List.of(
        new EvolutionOutcomeRecord("reviewer-calibration", 0.45, 0.72,
            Map.of("threshold", 0.75)),
        new EvolutionOutcomeRecord("reviewer-calibration", 0.42, 0.68,
            Map.of("threshold", 0.78)));
    var strategy = new CbrInformedAdjustmentStrategy(
        new ProportionalAdjustmentStrategy(), fewResults, 5, 0.1);

    var bounds = new AdjustmentBounds(0.30, 0.95, 0.01, 0.10, 1.0,
        AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
    var result = strategy.compute("threshold", 0.70, 0.45, 0.6, bounds,
        "reviewer-calibration", caseId);

    assertEquals("proportional", result.method());
  }

  @Test
  void usesCbrWhenSufficientPrecedents() {
    OutcomeQuery query = (category, limit) -> List.of(
        new EvolutionOutcomeRecord("reviewer-calibration", 0.44, 0.71,
            Map.of("threshold", 0.76)),
        new EvolutionOutcomeRecord("reviewer-calibration", 0.46, 0.73,
            Map.of("threshold", 0.75)),
        new EvolutionOutcomeRecord("reviewer-calibration", 0.43, 0.65,
            Map.of("threshold", 0.80)),
        new EvolutionOutcomeRecord("reviewer-calibration", 0.47, 0.70,
            Map.of("threshold", 0.74)),
        new EvolutionOutcomeRecord("reviewer-calibration", 0.42, 0.69,
            Map.of("threshold", 0.77)));
    var strategy = new CbrInformedAdjustmentStrategy(
        new ProportionalAdjustmentStrategy(), query, 5, 0.1);

    var bounds = new AdjustmentBounds(0.30, 0.95, 0.01, 0.10, 1.0,
        AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
    var result = strategy.compute("threshold", 0.70, 0.45, 0.6, bounds,
        "reviewer-calibration", caseId);

    assertEquals("cbr-informed", result.method());
    assertTrue(result.precedentCount() >= 5);
  }

  @Test
  void cbrConstrainedByBounds() {
    OutcomeQuery query = (category, limit) -> List.of(
        new EvolutionOutcomeRecord("test", 0.40, 0.80,
            Map.of("threshold", 0.99)),
        new EvolutionOutcomeRecord("test", 0.41, 0.78,
            Map.of("threshold", 0.98)),
        new EvolutionOutcomeRecord("test", 0.42, 0.76,
            Map.of("threshold", 0.97)),
        new EvolutionOutcomeRecord("test", 0.43, 0.75,
            Map.of("threshold", 0.96)),
        new EvolutionOutcomeRecord("test", 0.44, 0.74,
            Map.of("threshold", 0.95)));
    var strategy = new CbrInformedAdjustmentStrategy(
        new ProportionalAdjustmentStrategy(), query, 5, 0.1);

    var bounds = new AdjustmentBounds(0.30, 0.85, 0.01, 0.10, 1.0,
        AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
    var result = strategy.compute("threshold", 0.70, 0.45, 0.6, bounds,
        "test", caseId);

    assertTrue(result.newValue() <= 0.85, "CBR recommendation clamped to ceiling");
  }

  @Test
  void filtersOutPrecedentsWithoutHealthAfter() {
    OutcomeQuery query = (category, limit) -> List.of(
        new EvolutionOutcomeRecord("test", 0.45, Double.NaN,
            Map.of("threshold", 0.76)),
        new EvolutionOutcomeRecord("test", 0.44, Double.NaN,
            Map.of("threshold", 0.77)),
        new EvolutionOutcomeRecord("test", 0.46, Double.NaN,
            Map.of("threshold", 0.75)),
        new EvolutionOutcomeRecord("test", 0.43, Double.NaN,
            Map.of("threshold", 0.78)),
        new EvolutionOutcomeRecord("test", 0.42, Double.NaN,
            Map.of("threshold", 0.79)));
    var strategy = new CbrInformedAdjustmentStrategy(
        new ProportionalAdjustmentStrategy(), query, 5, 0.1);

    var bounds = new AdjustmentBounds(0.30, 0.95, 0.01, 0.10, 1.0,
        AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
    var result = strategy.compute("threshold", 0.70, 0.45, 0.6, bounds,
        "test", caseId);

    assertEquals("proportional", result.method(),
        "NaN healthAfter means cannot rank by effectiveness, fall back");
  }

  @Test
  void noAdjustmentWhenHealthAboveTarget() {
    OutcomeQuery query = (category, limit) -> List.of();
    var strategy = new CbrInformedAdjustmentStrategy(
        new ProportionalAdjustmentStrategy(), query, 5, 0.1);

    var bounds = new AdjustmentBounds(0.30, 0.95, 0.01, 0.10, 1.0,
        AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
    var result = strategy.compute("threshold", 0.70, 0.75, 0.6, bounds,
        "test", caseId);

    assertEquals(0.70, result.newValue());
  }
}
