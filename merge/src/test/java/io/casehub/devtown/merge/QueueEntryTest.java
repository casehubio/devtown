package io.casehub.devtown.merge;

import io.casehub.devtown.domain.queue.PriorityLane;
import io.casehub.devtown.queue.QueuedPr;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QueueEntryTest {

    @Test
    void shouldConstructQueueEntry() {
        QueuedPr pr = new QueuedPr(
            123,
            "casehubio/devtown",
            "abc123",
            "alice",
            0.85,
            PriorityLane.NORMAL,
            Instant.now(),
            Set.of()
        );
        UUID workItemId = UUID.randomUUID();

        QueueEntry entry = new QueueEntry(
            pr,
            workItemId,
            QueueEntryStatus.QUEUED,
            false,
            null
        );

        assertThat(entry.pr()).isEqualTo(pr);
        assertThat(entry.workItemId()).isEqualTo(workItemId);
        assertThat(entry.status()).isEqualTo(QueueEntryStatus.QUEUED);
        assertThat(entry.prioritized()).isFalse();
        assertThat(entry.batchId()).isNull();
    }

    @Test
    void shouldConstructPrioritizedEntry() {
        QueuedPr pr = new QueuedPr(
            456,
            "casehubio/platform",
            "def456",
            "bob",
            0.72,
            PriorityLane.HIGH,
            Instant.now(),
            Set.of()
        );
        UUID workItemId = UUID.randomUUID();

        QueueEntry entry = new QueueEntry(
            pr,
            workItemId,
            QueueEntryStatus.QUEUED,
            true,
            null
        );

        assertThat(entry.prioritized()).isTrue();
    }

    @Test
    void shouldConstructBatchedEntry() {
        QueuedPr pr = new QueuedPr(
            789,
            "casehubio/engine",
            "ghi789",
            "charlie",
            0.65,
            PriorityLane.NORMAL,
            Instant.now(),
            Set.of()
        );
        UUID workItemId = UUID.randomUUID();
        String batchId = "batch-2026-06-29-abc123";

        QueueEntry entry = new QueueEntry(
            pr,
            workItemId,
            QueueEntryStatus.IN_BATCH,
            false,
            batchId
        );

        assertThat(entry.status()).isEqualTo(QueueEntryStatus.IN_BATCH);
        assertThat(entry.batchId()).isEqualTo(batchId);
    }
}
