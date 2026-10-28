package io.casehub.devtown.app.evolution.worker;

import io.casehub.api.model.stigmergy.CapabilityAreaAssessment;
import io.casehub.devtown.app.evolution.ReviewQualityCapabilityArea;
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
public class RoutingAdjustmentWorker {

    static final double DEFAULT_BLEND_FACTOR = 0.60;
    static final double DEFAULT_CBR_WEIGHT   = 0.10;

    static final AdjustmentBounds BLEND_FACTOR_BOUNDS = new AdjustmentBounds(
            0.20, 1.0, 0.02, 0.15, 1.0,
            AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
    static final AdjustmentBounds CBR_WEIGHT_BOUNDS   = new AdjustmentBounds(
            0.0, 0.5, 0.02, 0.10, 0.5,
            AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);

    private final PreferenceWriter            preferenceWriter;
    private final PreferenceReader            preferenceReader;
    private final ReviewQualityCapabilityArea capabilityArea;
    private final AdjustmentStrategy          strategy;

    @Inject
    public RoutingAdjustmentWorker(
            PreferenceWriter preferenceWriter,
            PreferenceReader preferenceReader,
            ReviewQualityCapabilityArea capabilityArea,
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
                    String.format("Health %.2f >= target %.2f", health, TARGET_HEALTH));
        }
        String scopePath = "casehubio/devtown/trust-routing/" + capability;
        String namespace = "casehubio.devtown.trust-routing";

        List<Map<String, Object>> adjustments = new ArrayList<>();

        double currentBlend = preferenceReader.read(scopePath, namespace, "blend-factor").orElse(DEFAULT_BLEND_FACTOR);
        AdjustmentResult blendAdj = strategy.compute(
                "blend-factor", currentBlend, health, TARGET_HEALTH, BLEND_FACTOR_BOUNDS,
                "routing-adjustment", scope.caseId());
        if (blendAdj.oldValue() != blendAdj.newValue()) {
            preferenceWriter.write(scopePath, namespace, "blend-factor",
                                   String.valueOf(blendAdj.newValue()));
            adjustments.add(blendAdj.toMap());
        }

        double currentCbr = preferenceReader.read(scopePath, namespace, "cbr-weight").orElse(DEFAULT_CBR_WEIGHT);
        AdjustmentResult cbrAdj = strategy.compute(
                "cbr-weight", currentCbr, health, TARGET_HEALTH, CBR_WEIGHT_BOUNDS,
                "routing-adjustment", scope.caseId());
        if (cbrAdj.oldValue() != cbrAdj.newValue()) {
            preferenceWriter.write(scopePath, namespace, "cbr-weight",
                                   String.valueOf(cbrAdj.newValue()));
            adjustments.add(cbrAdj.toMap());
        }

        var output = new LinkedHashMap<String, Object>();
        output.put("category", "routing-adjustment");
        output.put("target", capability);
        output.put("adjustments", adjustments);
        output.put("healthBefore", health);
        output.put("status", STATUS_APPLIED);
        output.put("requiresPr", false);
        return WorkerResult.of(output);
    }
}
