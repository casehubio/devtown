package io.casehub.devtown.domain.trust;

import java.time.Duration;
import java.time.Instant;

public enum CacheMaturity {
    COLD(0, 5, Duration.ZERO),
    WARM(6, 20, Duration.ofDays(1)),
    HOT(21, 50, Duration.ofDays(3)),
    MATURE(51, Integer.MAX_VALUE, Duration.ofDays(7));

    private final int minObservations;
    private final int maxObservations;
    private final Duration staleDuration;

    CacheMaturity(int minObservations, int maxObservations, Duration staleDuration) {
        this.minObservations = minObservations;
        this.maxObservations = maxObservations;
        this.staleDuration = staleDuration;
    }

    public boolean isStale(Instant lastRefresh, Instant now) {
        if (lastRefresh == null) return true;
        if (this == COLD) return true;
        return Duration.between(lastRefresh, now).compareTo(staleDuration) > 0;
    }

    public static CacheMaturity forObservationCount(int count) {
        for (CacheMaturity m : values()) {
            if (count >= m.minObservations && count <= m.maxObservations) return m;
        }
        return MATURE;
    }
}
