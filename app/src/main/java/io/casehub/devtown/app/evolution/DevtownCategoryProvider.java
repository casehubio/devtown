package io.casehub.devtown.app.evolution;

import io.casehub.api.model.stigmergy.CategoryDescriptor;
import io.casehub.api.model.stigmergy.StageDescriptor;
import io.casehub.api.spi.improvement.ImprovementCategoryProvider;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class DevtownCategoryProvider implements ImprovementCategoryProvider {

  @Override
  public String domainId() {
    return "devtown";
  }

  @Override
  public List<CategoryDescriptor> categories() {
    return List.of(
        new CategoryDescriptor(
            "reviewer-calibration",
            "Reviewer Calibration",
            "Adjust reviewer trust weights when accuracy drifts",
            "devtown"),
        new CategoryDescriptor(
            "routing-adjustment",
            "Routing Adjustment",
            "Modify capability-to-reviewer routing rules for load/quality balance",
            "devtown"),
        new CategoryDescriptor(
            "sla-tuning",
            "SLA Tuning",
            "Adjust SLA thresholds when completion rates are too low or high",
            "devtown"),
        new CategoryDescriptor(
            "gate-tightening",
            "Gate Tightening",
            "Add or modify gate policies when quality signals degrade",
            "devtown"),
        new CategoryDescriptor(
            "queue-optimization",
            "Queue Optimization",
            "Adjust merge queue batch size and priority lanes for throughput",
            "devtown"));
  }

  @Override
  public List<StageDescriptor> stages() {
    return List.of(
        new StageDescriptor("analyze", "Analyze", 0, false, "devtown"),
        new StageDescriptor("propose", "Propose", 1, true, "devtown"),
        new StageDescriptor("review", "Review", 2, true, "devtown"),
        new StageDescriptor("apply", "Apply", 3, false, "devtown"),
        new StageDescriptor("observe", "Observe", 4, false, "devtown"));
  }
}
