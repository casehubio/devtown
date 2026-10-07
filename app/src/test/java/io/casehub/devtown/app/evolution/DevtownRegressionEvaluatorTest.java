package io.casehub.devtown.app.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.stigmergy.HealthScoreSnapshot;
import io.casehub.api.model.stigmergy.RegressionVerdict;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DevtownRegressionEvaluatorTest {

  private final DevtownRegressionEvaluator evaluator = new DevtownRegressionEvaluator();
  private final UUID caseId = UUID.randomUUID();

  @Test
  void evaluatorId_returns_devtown_health_delta() {
    assertThat(evaluator.evaluatorId()).isEqualTo("devtown-health-delta");
  }

  @Test
  void domainId_returns_devtown() {
    assertThat(evaluator.domainId()).isEqualTo("devtown");
  }

  @Test
  void no_regression_when_score_improves() {
    var baseline = new HealthScoreSnapshot(0.6, Instant.now(), Map.of());
    var current = new HealthScoreSnapshot(0.8, Instant.now(), Map.of());
    var verdict = evaluator.evaluate(caseId, baseline, current, "reviewer-calibration");
    assertThat(verdict).isInstanceOf(RegressionVerdict.NoRegression.class);
  }

  @Test
  void no_regression_when_score_drops_slightly() {
    var baseline = new HealthScoreSnapshot(0.8, Instant.now(), Map.of());
    var current = new HealthScoreSnapshot(0.75, Instant.now(), Map.of());
    var verdict = evaluator.evaluate(caseId, baseline, current, "sla-tuning");
    assertThat(verdict).isInstanceOf(RegressionVerdict.NoRegression.class);
  }

  @Test
  void regression_detected_when_score_drops_more_than_threshold() {
    var baseline = new HealthScoreSnapshot(0.8, Instant.now(), Map.of());
    var current = new HealthScoreSnapshot(0.6, Instant.now(), Map.of());
    var verdict = evaluator.evaluate(caseId, baseline, current, "reviewer-calibration");
    assertThat(verdict).isInstanceOf(RegressionVerdict.Detected.class);
    var detected = (RegressionVerdict.Detected) verdict;
    assertThat(detected.confidence()).isGreaterThan(0.0);
    assertThat(detected.reason()).contains("reviewer-calibration");
  }

  @Test
  void regression_detected_at_exact_threshold() {
    var baseline = new HealthScoreSnapshot(0.7, Instant.now(), Map.of());
    var current = new HealthScoreSnapshot(0.6, Instant.now(), Map.of());
    // delta = -0.1 exactly — this is the boundary
    var verdict = evaluator.evaluate(caseId, baseline, current, "queue-optimization");
    assertThat(verdict).isInstanceOf(RegressionVerdict.NoRegression.class);
  }

  @Test
  void regression_detected_just_past_threshold() {
    var baseline = new HealthScoreSnapshot(0.7, Instant.now(), Map.of());
    var current = new HealthScoreSnapshot(0.59, Instant.now(), Map.of());
    var verdict = evaluator.evaluate(caseId, baseline, current, "gate-tightening");
    assertThat(verdict).isInstanceOf(RegressionVerdict.Detected.class);
  }
}
