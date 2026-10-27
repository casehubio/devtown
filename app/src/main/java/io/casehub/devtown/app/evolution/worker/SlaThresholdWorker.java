package io.casehub.devtown.app.evolution.worker;

import io.casehub.api.model.stigmergy.CapabilityAreaAssessment;
import io.casehub.devtown.app.evolution.SlaComplianceCapabilityArea;
import io.casehub.devtown.domain.evolution.AdjustmentBounds;
import io.casehub.devtown.domain.evolution.AdjustmentResult;
import io.casehub.devtown.domain.evolution.AdjustmentStrategy;
import io.casehub.devtown.domain.evolution.PreferenceWriter;
import io.casehub.worker.api.WorkerResult;
import io.casehub.worker.api.WorkerScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.casehub.devtown.app.evolution.worker.EvolutionWorkerConstants.STATUS_APPLIED;
import static io.casehub.devtown.app.evolution.worker.EvolutionWorkerConstants.TARGET_HEALTH;

@ApplicationScoped
public class SlaThresholdWorker {

  static final AdjustmentBounds SLA_CRITICAL_BOUNDS = new AdjustmentBounds(
      30, 240, 10, 60, 200.0,
      AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
  static final AdjustmentBounds SLA_HIGH_BOUNDS = new AdjustmentBounds(
      60, 720, 15, 120, 400.0,
      AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
  static final AdjustmentBounds SLA_NORMAL_BOUNDS = new AdjustmentBounds(
      120, 1440, 30, 240, 800.0,
      AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
  static final AdjustmentBounds ESCALATION_BOUNDS = new AdjustmentBounds(
      2, 24, 1, 4, 10.0,
      AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);

  static final double MIN_CRITICAL_HIGH_GAP = 30;
  static final double MIN_HIGH_NORMAL_GAP = 60;

  private final PreferenceWriter preferenceWriter;
  private final SlaComplianceCapabilityArea capabilityArea;
  private final AdjustmentStrategy strategy;

  @Inject
  public SlaThresholdWorker(
      PreferenceWriter preferenceWriter,
      SlaComplianceCapabilityArea capabilityArea,
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

    AdjustmentResult criticalAdj = strategy.compute(
        "sla.critical", 60, health, TARGET_HEALTH, SLA_CRITICAL_BOUNDS,
        "sla-tuning", scope.caseId());
    AdjustmentResult highAdj = strategy.compute(
        "sla.high", 240, health, TARGET_HEALTH, SLA_HIGH_BOUNDS,
        "sla-tuning", scope.caseId());
    AdjustmentResult normalAdj = strategy.compute(
        "sla.normal", 480, health, TARGET_HEALTH, SLA_NORMAL_BOUNDS,
        "sla-tuning", scope.caseId());

    double critical = criticalAdj.newValue();
    double high = highAdj.newValue();
    double normal = normalAdj.newValue();

    if (high < critical + MIN_CRITICAL_HIGH_GAP) {
      high = critical + MIN_CRITICAL_HIGH_GAP;
    }
    if (normal < high + MIN_HIGH_NORMAL_GAP) {
      normal = high + MIN_HIGH_NORMAL_GAP;
    }

    String mqScope = "casehubio/devtown/merge-queue";
    String mqNs = "devtown.merge-queue.sla";

    if (critical != criticalAdj.oldValue()) {
      preferenceWriter.write(mqScope, mqNs, "CRITICAL", minutesToDuration(critical));
      adjustments.add(withClamped(criticalAdj, critical));
    }
    if (high != highAdj.oldValue()) {
      preferenceWriter.write(mqScope, mqNs, "HIGH", minutesToDuration(high));
      adjustments.add(withClamped(highAdj, high));
    }
    if (normal != normalAdj.oldValue()) {
      preferenceWriter.write(mqScope, mqNs, "NORMAL", minutesToDuration(normal));
      adjustments.add(withClamped(normalAdj, normal));
    }

    AdjustmentResult escAdj = strategy.compute(
        "escalation-hours", 8, health, TARGET_HEALTH, ESCALATION_BOUNDS,
        "sla-tuning", scope.caseId());
    if (escAdj.oldValue() != escAdj.newValue()) {
      preferenceWriter.write("casehubio/devtown/sla", "devtown.sla",
          "escalation-hours", String.valueOf(Math.round(escAdj.newValue())));
      adjustments.add(escAdj.toMap());
    }

    var output = new LinkedHashMap<String, Object>();
    output.put("category", "sla-tuning");
    output.put("target", "sla-compliance");
    output.put("adjustments", adjustments);
    output.put("healthBefore", health);
    output.put("status", STATUS_APPLIED);
    output.put("requiresPr", false);
    return WorkerResult.of(output);
  }

  static String minutesToDuration(double minutes) {
    return Duration.ofMinutes(Math.round(minutes)).toString();
  }

  private static Map<String, Object> withClamped(AdjustmentResult adj, double clampedValue) {
    var map = new LinkedHashMap<String, Object>();
    map.put("parameter", adj.parameter());
    map.put("oldValue", adj.oldValue());
    map.put("newValue", clampedValue);
    map.put("method", adj.method());
    map.put("precedentCount", adj.precedentCount());
    map.put("rationale", adj.rationale());
    map.put("duration", minutesToDuration(clampedValue));
    return map;
  }
}
