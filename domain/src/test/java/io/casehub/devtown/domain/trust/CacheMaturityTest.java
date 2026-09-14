package io.casehub.devtown.domain.trust;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class CacheMaturityTest {

    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");

    @Test
    void forObservationCount_cold() {
        assertEquals(CacheMaturity.COLD, CacheMaturity.forObservationCount(0));
        assertEquals(CacheMaturity.COLD, CacheMaturity.forObservationCount(5));
    }

    @Test
    void forObservationCount_warm() {
        assertEquals(CacheMaturity.WARM, CacheMaturity.forObservationCount(6));
        assertEquals(CacheMaturity.WARM, CacheMaturity.forObservationCount(20));
    }

    @Test
    void forObservationCount_hot() {
        assertEquals(CacheMaturity.HOT, CacheMaturity.forObservationCount(21));
        assertEquals(CacheMaturity.HOT, CacheMaturity.forObservationCount(50));
    }

    @Test
    void forObservationCount_mature() {
        assertEquals(CacheMaturity.MATURE, CacheMaturity.forObservationCount(51));
        assertEquals(CacheMaturity.MATURE, CacheMaturity.forObservationCount(500));
    }

    @Test
    void cold_alwaysStale() {
        assertTrue(CacheMaturity.COLD.isStale(NOW.minus(Duration.ofSeconds(1)), NOW));
        assertTrue(CacheMaturity.COLD.isStale(NOW, NOW));
    }

    @Test
    void warm_staleAfterOneDay() {
        assertFalse(CacheMaturity.WARM.isStale(NOW.minus(Duration.ofHours(23)), NOW));
        assertTrue(CacheMaturity.WARM.isStale(NOW.minus(Duration.ofHours(25)), NOW));
    }

    @Test
    void hot_staleAfterThreeDays() {
        assertFalse(CacheMaturity.HOT.isStale(NOW.minus(Duration.ofDays(2)), NOW));
        assertTrue(CacheMaturity.HOT.isStale(NOW.minus(Duration.ofDays(4)), NOW));
    }

    @Test
    void mature_staleAfterSevenDays() {
        assertFalse(CacheMaturity.MATURE.isStale(NOW.minus(Duration.ofDays(6)), NOW));
        assertTrue(CacheMaturity.MATURE.isStale(NOW.minus(Duration.ofDays(8)), NOW));
    }

    @Test
    void nullLastRefresh_alwaysStale() {
        assertTrue(CacheMaturity.MATURE.isStale(null, NOW));
    }
}
