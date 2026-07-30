package io.casehub.devtown.app.persistence;

import io.casehub.devtown.review.sla.SlaCalibrationRecord;
import io.casehub.devtown.review.sla.SlaCalibrationStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class JpaSlaCalibrationStoreTest {

    @Inject SlaCalibrationStore store;

    @Test
    @Transactional
    void save_and_findLatest() {
        var record = new SlaCalibrationRecord(
            UUID.randomUUID(), "pr-review", "casehubio/devtown/pr-review",
            Duration.ofMinutes(45), Duration.ofMinutes(10), Duration.ofHours(3),
            12, UUID.randomUUID(), Instant.now());

        store.save(record);

        var found = store.findLatest("pr-review", "casehubio/devtown/pr-review");
        assertThat(found).isPresent();
        assertThat(found.get().median()).isEqualTo(Duration.ofMinutes(45));
        assertThat(found.get().precedentCount()).isEqualTo(12);
    }

    @Test
    @Transactional
    void findLatest_returns_most_recent() {
        var older = new SlaCalibrationRecord(
            UUID.randomUUID(), "pr-review", "casehubio/devtown/pr-review",
            Duration.ofMinutes(30), Duration.ofMinutes(5), Duration.ofHours(2),
            8, UUID.randomUUID(), Instant.now().minusSeconds(3600));
        var newer = new SlaCalibrationRecord(
            UUID.randomUUID(), "pr-review", "casehubio/devtown/pr-review",
            Duration.ofMinutes(50), Duration.ofMinutes(15), Duration.ofHours(4),
            20, UUID.randomUUID(), Instant.now());

        store.save(older);
        store.save(newer);

        var found = store.findLatest("pr-review", "casehubio/devtown/pr-review");
        assertThat(found).isPresent();
        assertThat(found.get().precedentCount()).isEqualTo(20);
    }

    @Test
    void findLatest_no_match_returns_empty() {
        var found = store.findLatest("nonexistent", "no/scope");
        assertThat(found).isEmpty();
    }
}
