package io.casehub.devtown.domain.evolution;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProportionalAdjustmentStrategyTest {

  private final ProportionalAdjustmentStrategy strategy = new ProportionalAdjustmentStrategy();
  private final UUID caseId = UUID.randomUUID();

  @Test
  void adjustsProportionallyToHealthGap() {
    var bounds = new AdjustmentBounds(0.30, 0.95, 0.01, 0.10, 1.0,
        AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
    var result = strategy.compute("threshold", 0.70, 0.45, 0.6, bounds,
        "reviewer-calibration", caseId);

    assertEquals("threshold", result.parameter());
    assertEquals(0.70, result.oldValue());
    assertTrue(result.newValue() > 0.70, "should increase when health is low");
    assertTrue(result.newValue() <= 0.80, "step capped by maxStep 0.10");
    assertEquals("proportional", result.method());
    assertEquals(0, result.precedentCount());
  }

  @Test
  void clampsToFloor() {
    var bounds = new AdjustmentBounds(0.30, 0.95, 0.01, 0.50, 1.0,
        AdjustmentBounds.Direction.DECREASE_ON_LOW_HEALTH);
    var result = strategy.compute("threshold", 0.35, 0.45, 0.6, bounds,
        "test", caseId);

    assertTrue(result.newValue() >= 0.30, "should not go below floor");
  }

  @Test
  void clampsToCeiling() {
    var bounds = new AdjustmentBounds(0.30, 0.95, 0.01, 0.50, 1.0,
        AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
    var result = strategy.compute("threshold", 0.90, 0.10, 0.6, bounds,
        "test", caseId);

    assertTrue(result.newValue() <= 0.95, "should not exceed ceiling");
  }

  @Test
  void respectsMinStep() {
    var bounds = new AdjustmentBounds(0.30, 0.95, 0.05, 0.10, 0.1,
        AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
    var result = strategy.compute("threshold", 0.70, 0.58, 0.6, bounds,
        "test", caseId);

    assertTrue(Math.abs(result.newValue() - result.oldValue()) >= 0.05,
        "adjustment should be at least minStep");
  }

  @Test
  void noAdjustmentWhenHealthAboveTarget() {
    var bounds = new AdjustmentBounds(0.30, 0.95, 0.01, 0.10, 1.0,
        AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
    var result = strategy.compute("threshold", 0.70, 0.75, 0.6, bounds,
        "test", caseId);

    assertEquals(0.70, result.newValue(), "no change when health is above target");
  }

  @Test
  void decreasesWhenDirectionIsDecrease() {
    var bounds = new AdjustmentBounds(100, 2000, 10, 200, 1000.0,
        AdjustmentBounds.Direction.DECREASE_ON_LOW_HEALTH);
    var result = strategy.compute("human-approval-threshold", 500, 0.45, 0.6,
        bounds, "gate-tightening", caseId);

    assertTrue(result.newValue() < 500, "should decrease when direction is DECREASE");
  }

  @Test
  void toMapProducesExpectedKeys() {
    var result = new AdjustmentResult("param", 0.5, 0.6, "proportional", 0, "test rationale");
    var map = result.toMap();

    assertEquals("param", map.get("parameter"));
    assertEquals(0.5, map.get("oldValue"));
    assertEquals(0.6, map.get("newValue"));
    assertEquals("proportional", map.get("method"));
    assertEquals(0, map.get("precedentCount"));
    assertEquals("test rationale", map.get("rationale"));
  }
}
