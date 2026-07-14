package io.casehub.devtown.queue;

import io.casehub.devtown.domain.cbr.PrFeatureVector;
import io.casehub.devtown.domain.cbr.Precedent;

import java.util.List;
import java.util.function.BiFunction;

public class CbrBatchRiskAssessor implements BatchRiskAssessor {

    private final BiFunction<PrFeatureVector, String, List<Precedent>> precedentLookup;

    public CbrBatchRiskAssessor(BiFunction<PrFeatureVector, String, List<Precedent>> precedentLookup) {
        this.precedentLookup = precedentLookup;
    }

    @Override
    public List<QueuedPr> assessRisk(List<QueuedPr> candidates, String repository, String tenantId) {
        return candidates.stream()
            .map(pr -> pr.withRiskScore(scoreRisk(pr, repository, tenantId)))
            .toList();
    }

    private double scoreRisk(QueuedPr pr, String repository, String tenantId) {
        var vector = PrFeatureVector.from(
            repository, pr.number(), pr.author(), 0, List.of());

        List<Precedent> similar = precedentLookup.apply(vector, tenantId);
        if (similar.isEmpty()) return 0.0;

        long failures = similar.stream()
            .filter(p -> isFailureOutcome(p.outcome()))
            .count();
        return (double) failures / similar.size();
    }

    private static boolean isFailureOutcome(String outcome) {
        return outcome != null && (
            outcome.contains("fail") || outcome.contains("reject") || outcome.contains("FAULTED"));
    }
}
