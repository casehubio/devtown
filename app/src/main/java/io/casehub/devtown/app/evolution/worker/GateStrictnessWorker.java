package io.casehub.devtown.app.evolution.worker;

import io.casehub.api.model.stigmergy.CapabilityAreaAssessment;
import io.casehub.devtown.app.evolution.CiReliabilityCapabilityArea;
import io.casehub.devtown.domain.evolution.AdjustmentBounds;
import io.casehub.devtown.domain.evolution.AdjustmentResult;
import io.casehub.devtown.domain.evolution.AdjustmentStrategy;
import io.casehub.devtown.domain.evolution.PreferenceWriter;
import io.casehub.worker.api.WorkerResult;
import io.casehub.worker.api.WorkerScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.casehub.devtown.app.evolution.worker.EvolutionWorkerConstants.STATUS_APPLIED;
import static io.casehub.devtown.app.evolution.worker.EvolutionWorkerConstants.TARGET_HEALTH;

@ApplicationScoped
public class GateStrictnessWorker {

  static final AdjustmentBounds TRUST_GATE_BOUNDS = new AdjustmentBounds(
      0.10, 0.60, 0.02, 0.10, 1.0,
      AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
  static final AdjustmentBounds HUMAN_APPROVAL_BOUNDS = new AdjustmentBounds(
      100, 2000, 25, 200, 1000.0,
      AdjustmentBounds.Direction.DECREASE_ON_LOW_HEALTH);
  static final AdjustmentBounds MIN_REVIEWS_BOUNDS = new AdjustmentBounds(
      1, 3, 1, 1, 5.0,
      AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);

  private final PreferenceWriter preferenceWriter;
  private final CiReliabilityCapabilityArea capabilityArea;
  private final AdjustmentStrategy strategy;

  @Inject
  public GateStrictnessWorker(
      PreferenceWriter preferenceWriter,
      CiReliabilityCapabilityArea capabilityArea,
      AdjustmentStrategy strategy) {
    this.preferenceWriter = preferenceWriter;
    this.capabilityArea = capabilityArea;
    this.strategy = strategy;
  }

  public WorkerResult<Map<String, Object>> apply(
      Map<String, Object> input, WorkerScope scope) {

    String tenancyId = (String) scope.accumulatedState().get("tenancyId");
    if (tenancyId == null) {
      return WorkerResult.failed("Missing 'tenancyId' in accumulated state");
    }

    CapabilityAreaAssessment assessment = capabilityArea.assess(scope.caseId(), tenancyId);
    double health = assessment.healthScore();

    if (health >= TARGET_HEALTH) {
      return WorkerResult.declined(
          String.format("Health %.2f >= target %.2f", health, TARGET_HEALTH));
    }

    List<Map<String, Object>> adjustments = new ArrayList<>();

    AdjustmentResult gateAdj = strategy.compute(
        "min-obligor-trust", 0.30, health, TARGET_HEALTH, TRUST_GATE_BOUNDS,
        "gate-tightening", scope.caseId());
    if (gateAdj.oldValue() != gateAdj.newValue()) {
      preferenceWriter.write("casehubio/devtown/trust-gate",
          "casehubio.devtown.trust-gate", "min-obligor-trust",
          String.valueOf(gateAdj.newValue()));
      adjustments.add(gateAdj.toMap());
    }

    AdjustmentResult approvalAdj = strategy.compute(
        "human-approval-threshold", 500, health, TARGET_HEALTH,
        HUMAN_APPROVAL_BOUNDS, "gate-tightening", scope.caseId());
    if (approvalAdj.oldValue() != approvalAdj.newValue()) {
      preferenceWriter.write("casehubio/devtown/pr-review",
          "devtown.pr-review", "human-approval-threshold",
          String.valueOf(Math.round(approvalAdj.newValue())));
      adjustments.add(approvalAdj.toMap());
    }

    AdjustmentResult reviewsAdj = strategy.compute(
        "min-approved-reviews", 1, health, TARGET_HEALTH,
        MIN_REVIEWS_BOUNDS, "gate-tightening", scope.caseId());
    if (reviewsAdj.oldValue() != reviewsAdj.newValue()) {
      preferenceWriter.write("casehubio/devtown/risk",
          "casehubio.devtown.risk", "merge-min-approved-reviews",
          String.valueOf(Math.round(reviewsAdj.newValue())));
      adjustments.add(reviewsAdj.toMap());
    }

    var output = new LinkedHashMap<String, Object>();
    output.put("category", "gate-tightening");
    output.put("target", "ci-reliability");
    output.put("adjustments", adjustments);
    output.put("healthBefore", health);
    output.put("status", STATUS_APPLIED);
    output.put("requiresPr", false);
    return WorkerResult.of(output);
  }
}
