package io.casehub.devtown.app.evolution;

import io.casehub.api.model.stigmergy.HealthScoreSnapshot;
import io.casehub.api.model.stigmergy.RegressionVerdict;
import io.casehub.api.spi.improvement.RegressionEvaluator;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class DevtownRegressionEvaluator implements RegressionEvaluator {

  private static final double REGRESSION_THRESHOLD = -0.1;

  @Override
  public String evaluatorId() {
    return "devtown-health-delta";
  }

  @Override
  public String domainId() {
    return "devtown";
  }

  @Override
  public RegressionVerdict evaluate(
      UUID caseId, HealthScoreSnapshot baseline, HealthScoreSnapshot current, String category) {
    double delta = current.score() - baseline.score();
    if (delta < REGRESSION_THRESHOLD) {
      return new RegressionVerdict.Detected(
          Math.abs(delta),
          "Health score dropped by "
              + String.format("%.1f%%", Math.abs(delta) * 100)
              + " after "
              + category
              + " improvement");
    }
    return new RegressionVerdict.NoRegression();
  }
}
