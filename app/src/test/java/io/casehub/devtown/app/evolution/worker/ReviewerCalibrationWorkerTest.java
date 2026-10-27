package io.casehub.devtown.app.evolution.worker;

import io.casehub.api.model.stigmergy.CapabilityAreaAssessment;
import io.casehub.devtown.app.evolution.ReviewerTrustCapabilityArea;
import io.casehub.devtown.domain.evolution.AdjustmentStrategy;
import io.casehub.devtown.domain.evolution.PreferenceWriter;
import io.casehub.devtown.domain.evolution.ProportionalAdjustmentStrategy;
import io.casehub.worker.api.WorkerOutcome;
import io.casehub.worker.api.WorkerResult;
import io.casehub.worker.api.WorkerScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ReviewerCalibrationWorkerTest {

  private ReviewerCalibrationWorker worker;
  private PreferenceWriter preferenceWriter;
  private ReviewerTrustCapabilityArea capabilityArea;
  private WorkerScope scope;
  private final UUID caseId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    preferenceWriter = mock(PreferenceWriter.class);
    capabilityArea = mock(ReviewerTrustCapabilityArea.class);
    AdjustmentStrategy strategy = new ProportionalAdjustmentStrategy();

    worker = new ReviewerCalibrationWorker(preferenceWriter, capabilityArea, strategy);

    scope = mock(WorkerScope.class);
    when(scope.caseId()).thenReturn(caseId);
    when(scope.accumulatedState()).thenReturn(Map.of("tenancyId", "casehubio"));
  }

  private CapabilityAreaAssessment assessment(double healthScore) {
    return new CapabilityAreaAssessment("reviewer-trust", healthScore,
        CapabilityAreaAssessment.LandscapePosition.AT_PARITY, 0.5, 0.3, 1.67, Instant.now());
  }

  @Test
  void adjustsThresholdWhenHealthIsLow() {
    when(capabilityArea.assess(any(), anyString())).thenReturn(assessment(0.45));

    Map<String, Object> input = Map.of(
        "category", "reviewer-calibration",
        "target", "security-review");

    @SuppressWarnings("unchecked")
    WorkerResult<Map<String, Object>> result = worker.apply(input, scope);

    assertInstanceOf(WorkerOutcome.Success.class, result.outcome());
    Map<String, Object> output = result.output();
    assertEquals("reviewer-calibration", output.get("category"));
    assertEquals("APPLIED", output.get("status"));
    assertEquals(false, output.get("requiresPr"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> adjustments =
        (List<Map<String, Object>>) output.get("adjustments");
    assertFalse(adjustments.isEmpty(), "should have at least one adjustment");

    verify(preferenceWriter, atLeastOnce()).write(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void declinesWhenHealthAboveTarget() {
    when(capabilityArea.assess(any(), anyString())).thenReturn(assessment(0.75));

    Map<String, Object> input = Map.of(
        "category", "reviewer-calibration",
        "target", "security-review");

    @SuppressWarnings("unchecked")
    WorkerResult<Map<String, Object>> result = worker.apply(input, scope);

    assertInstanceOf(WorkerOutcome.Declined.class, result.outcome());
    verify(preferenceWriter, never()).write(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void outputIncludesHealthBefore() {
    when(capabilityArea.assess(any(), anyString())).thenReturn(assessment(0.45));

    Map<String, Object> input = Map.of(
        "category", "reviewer-calibration",
        "target", "security-review");

    @SuppressWarnings("unchecked")
    WorkerResult<Map<String, Object>> result = worker.apply(input, scope);

    assertEquals(0.45, result.output().get("healthBefore"));
  }

  @Test
  void missingTenancyId_fails() {
    when(scope.accumulatedState()).thenReturn(Map.of());
    var result = worker.apply(
        Map.of("category", "reviewer-calibration", "target", "security-review"), scope);
    assertInstanceOf(WorkerOutcome.Failed.class, result.outcome());
  }

  @Test
  void missingTarget_fails() {
    var result = worker.apply(Map.of("category", "reviewer-calibration"), scope);
    assertInstanceOf(WorkerOutcome.Failed.class, result.outcome());
  }
}
