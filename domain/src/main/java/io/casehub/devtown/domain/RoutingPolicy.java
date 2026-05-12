package io.casehub.devtown.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public record RoutingPolicy(
    OptionalDouble threshold,
    OptionalInt minimumObservations,
    OptionalDouble borderlineMargin,
    Optional<String> fallbackType,
    String rationale
) {
    public RoutingPolicy {
        Objects.requireNonNull(threshold, "threshold must not be null");
        Objects.requireNonNull(minimumObservations, "minimumObservations must not be null");
        Objects.requireNonNull(borderlineMargin, "borderlineMargin must not be null");
        Objects.requireNonNull(fallbackType, "fallbackType must not be null");
        Objects.requireNonNull(rationale, "rationale must not be null");
    }

    public boolean isBootstrap(int agentObservations) {
        return minimumObservations.isPresent()
            && agentObservations < minimumObservations.getAsInt();
    }

    public boolean isBorderline(double trustScore) {
        return borderlineMargin.isPresent() && threshold.isPresent()
            && trustScore >= threshold.getAsDouble()
            && trustScore < threshold.getAsDouble() + borderlineMargin.getAsDouble();
    }
}
