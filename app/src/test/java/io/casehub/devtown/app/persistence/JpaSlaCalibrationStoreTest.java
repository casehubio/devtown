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

    @Test
    @Transactional
    void saveAll_and_findLatestCalibration() {
        java.time.Instant now    = java.time.Instant.now();
        UUID              caseId = UUID.randomUUID();
        var records = java.util.List.of(
                new SlaCalibrationRecord(UUID.randomUUID(), "pr-review",
                                         "casehubio/devtown/pr-review",
                                         Duration.ofMinutes(45), Duration.ofMinutes(10), Duration.ofHours(3),
                                         12, caseId, now),
                new SlaCalibrationRecord(UUID.randomUUID(), "code-analysis",
                                         "casehubio/devtown/pr-review",
                                         Duration.ofMinutes(4), Duration.ofMinutes(2), Duration.ofMinutes(8),
                                         10, caseId, now),
                new SlaCalibrationRecord(UUID.randomUUID(), "security-review",
                                         "casehubio/devtown/pr-review",
                                         Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofMinutes(20),
                                         8, caseId, now));

        store.saveAll(records);

        var latest = store.findLatestCalibration("casehubio/devtown/pr-review");
        assertThat(latest).hasSize(3);
        assertThat(latest.stream().map(SlaCalibrationRecord::capability).toList())
                .containsExactlyInAnyOrder("pr-review", "code-analysis", "security-review");
    }

    @Test
    @Transactional
    void findLatestCalibration_returnsOnlyLatestBatch() {
        java.time.Instant older = java.time.Instant.now().minusSeconds(3600);
        java.time.Instant newer = java.time.Instant.now();
        store.saveAll(java.util.List.of(
                new SlaCalibrationRecord(UUID.randomUUID(), "pr-review",
                                         "casehubio/devtown/pr-review",
                                         Duration.ofMinutes(30), Duration.ofMinutes(5), Duration.ofHours(2),
                                         8, UUID.randomUUID(), older)));
        store.saveAll(java.util.List.of(
                new SlaCalibrationRecord(UUID.randomUUID(), "pr-review",
                                         "casehubio/devtown/pr-review",
                                         Duration.ofMinutes(50), Duration.ofMinutes(15), Duration.ofHours(4),
                                         20, UUID.randomUUID(), newer),
                new SlaCalibrationRecord(UUID.randomUUID(), "code-analysis",
                                         "casehubio/devtown/pr-review",
                                         Duration.ofMinutes(5), Duration.ofMinutes(2), Duration.ofMinutes(10),
                                         18, UUID.randomUUID(), newer)));

        var latest = store.findLatestCalibration("casehubio/devtown/pr-review");
        assertThat(latest).hasSize(2);
        assertThat(latest.stream().map(SlaCalibrationRecord::precedentCount).toList())
                .allMatch(c -> c >= 18);
    }
}
