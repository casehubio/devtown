package io.casehub.devtown.domain.sla;

import io.casehub.devtown.domain.cbr.Precedent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SlaEstimator {

    public static Optional<SlaEstimate> estimate(List<Precedent> precedents) {
        List<Duration> totalDurations = precedents.stream()
                                                  .map(Precedent::completionTime)
                                                  .filter(Objects::nonNull)
                                                  .filter(d -> !d.isNegative() && !d.isZero())
                                                  .sorted()
                                                  .toList();

        if (totalDurations.isEmpty()) {return Optional.empty();}

        DurationStats overall = statsFrom(totalDurations);

        Map<String, List<Duration>> perCap = new LinkedHashMap<>();
        for (Precedent p : precedents) {
            for (var entry : p.capabilityOutcomes().entrySet()) {
                Duration d = entry.getValue().duration();
                if (d != null && !d.isNegative()) {
                    perCap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(d);
                }
            }
        }
        Map<String, DurationStats> breakdown = new LinkedHashMap<>();
        perCap.forEach((cap, durations) -> {
            List<Duration> sorted = durations.stream().sorted().toList();
            breakdown.put(cap, statsFrom(sorted));
        });

        return Optional.of(new SlaEstimate(overall, breakdown));}

    private static DurationStats statsFrom(List<Duration> sorted) {
        return new DurationStats(
                sorted.get(sorted.size() / 2), sorted.getFirst(),
                sorted.getLast(), sorted.size());
    }


    private SlaEstimator() {}
}
