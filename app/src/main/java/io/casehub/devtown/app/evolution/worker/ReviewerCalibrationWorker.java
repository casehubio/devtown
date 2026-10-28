package io.casehub.devtown.app.evolution.worker;

import io.casehub.api.model.stigmergy.CapabilityAreaAssessment;
import io.casehub.devtown.app.evolution.ReviewerTrustCapabilityArea;
import io.casehub.devtown.domain.evolution.AdjustmentBounds;
import io.casehub.devtown.domain.evolution.AdjustmentResult;
import io.casehub.devtown.domain.evolution.AdjustmentStrategy;
import io.casehub.devtown.domain.evolution.PreferenceReader;
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
public class ReviewerCalibrationWorker {

    static final double DEFAULT_THRESHOLD  = 0.70;
    static final double DEFAULT_MIN_OBS    = 10;
    static final double DEFAULT_BORDERLINE = 0.05;

    static final AdjustmentBounds THRESHOLD_BOUNDS  = new AdjustmentBounds(
            0.30, 0.95, 0.01, 0.10, 1.0,
            AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
    static final AdjustmentBounds MIN_OBS_BOUNDS    = new AdjustmentBounds(
            3, 30, 1, 5, 10.0,
            AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
    static final AdjustmentBounds BORDERLINE_BOUNDS = new AdjustmentBounds(
            0.02, 0.15, 0.005, 0.03, 0.5,
            AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);

    private final PreferenceWriter            preferenceWriter;
    private final PreferenceReader            preferenceReader;
    private final ReviewerTrustCapabilityArea capabilityArea;
    private final AdjustmentStrategy          strategy;

    @Inject
    public ReviewerCalibrationWorker(
            PreferenceWriter preferenceWriter,
            PreferenceReader preferenceReader,
            ReviewerTrustCapabilityArea capabilityArea,
            AdjustmentStrategy strategy) {
        this.preferenceWriter = preferenceWriter;
        this.preferenceReader = preferenceReader;
        this.capabilityArea   = capabilityArea;
        this.strategy         = strategy;
    }

    public WorkerResult<Map<String, Object>> apply(
            Map<String, Object> input, WorkerScope scope) {

        String tenancyId = (String) scope.accumulatedState().get("tenancyId");
        if (tenancyId == null) {
            return WorkerResult.failed("Missing 'tenancyId' in accumulated state");
        }
        String capability = (String) input.get("target");
        if (capability == null) {
            return WorkerResult.failed("Missing 'target' in input");
        }

        CapabilityAreaAssessment assessment = capabilityArea.assess(scope.caseId(), tenancyId);
        double                   health     = assessment.healthScore();

        if (health >= TARGET_HEALTH) {
            return WorkerResult.declined(
                    String.format("Health %.2f >= target %.2f, no calibration needed",
                                  health, TARGET_HEALTH));
        }

        String scopePath = "casehubio/devtown/trust-routing/" + capability;
        String namespace = "casehubio.devtown.trust-routing";

        List<Map<String, Object>> adjustments = new ArrayList<>();

        double currentThreshold = preferenceReader.read(scopePath, namespace, "threshold").orElse(DEFAULT_THRESHOLD);
        AdjustmentResult thresholdAdj = strategy.compute(
                "threshold", currentThreshold, health, TARGET_HEALTH, THRESHOLD_BOUNDS,
                "reviewer-calibration", scope.caseId());
        if (thresholdAdj.oldValue() != thresholdAdj.newValue()) {
            preferenceWriter.write(scopePath, namespace, "threshold",
                                   String.valueOf(thresholdAdj.newValue()));
            adjustments.add(thresholdAdj.toMap());
        }

        double currentMinObs = preferenceReader.read(scopePath, namespace, "minimum-observations").orElse(DEFAULT_MIN_OBS);
        AdjustmentResult minObsAdj = strategy.compute(
                "minimum-observations", currentMinObs, health, TARGET_HEALTH, MIN_OBS_BOUNDS,
                "reviewer-calibration", scope.caseId());
        if (minObsAdj.oldValue() != minObsAdj.newValue()) {
            preferenceWriter.write(scopePath, namespace, "minimum-observations",
                                   String.valueOf(Math.round(minObsAdj.newValue())));
            adjustments.add(minObsAdj.toMap());
        }

        double currentBorderline = preferenceReader.read(scopePath, namespace, "borderline-margin").orElse(DEFAULT_BORDERLINE);
        AdjustmentResult borderlineAdj = strategy.compute(
                "borderline-margin", currentBorderline, health, TARGET_HEALTH, BORDERLINE_BOUNDS,
                "reviewer-calibration", scope.caseId());
        if (borderlineAdj.oldValue() != borderlineAdj.newValue()) {
            preferenceWriter.write(scopePath, namespace, "borderline-margin",
                                   String.valueOf(borderlineAdj.newValue()));
            adjustments.add(borderlineAdj.toMap());
        }

        var output = new LinkedHashMap<String, Object>();
        output.put("category", "reviewer-calibration");
        output.put("target", capability);
        output.put("adjustments", adjustments);
        output.put("healthBefore", health);
        output.put("status", STATUS_APPLIED);
        output.put("requiresPr", false);

        return WorkerResult.of(output);
    }
}
