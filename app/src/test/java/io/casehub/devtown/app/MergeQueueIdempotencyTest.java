package io.casehub.devtown.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.devtown.domain.queue.PriorityLane;
import io.casehub.devtown.merge.MergeQueueStore;
import io.casehub.devtown.merge.QueueEntry;
import io.casehub.devtown.queue.QueuedPr;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Idempotency test for the merge queue.
 *
 * <p>Verifies that duplicate enqueue for the same {@code (prNumber, repository)}
 * composite key is silently ignored — no duplicate entries created, no exception thrown.
 * This prevents double-admission from any path (CasePlanModel binding, MCP tool, webhook).
 */
@QuarkusTest
class MergeQueueIdempotencyTest {

    @Inject MergeQueueStore store;

    @Inject
    @PersistenceUnit("qhorus")
    EntityManager em;

    @BeforeEach
    @Transactional
    void cleanAll() {
        em.createQuery("DELETE FROM QueuedPrEntity").executeUpdate();
        em.createQuery("DELETE FROM ActiveBatchEntity").executeUpdate();
    }

    @Test
    void duplicateEnqueue_samePrAndRepo_silentlyIgnored() {
        QueuedPr pr = new QueuedPr(801, "casehubio/devtown", "sha-801", "alice",
            0.8, PriorityLane.NORMAL, Instant.now(), Set.of());
        UUID workItemId1 = UUID.randomUUID();
        UUID workItemId2 = UUID.randomUUID();

        store.enqueue(pr, workItemId1);
        store.enqueue(pr, workItemId2);  // duplicate — should be no-op

        List<QueueEntry> queued = store.queued();
        assertThat(queued).hasSize(1);
        // First workItemId should be preserved
        assertThat(queued.get(0).workItemId()).isEqualTo(workItemId1);
    }

    @Test
    void duplicateEnqueue_samePrDifferentRepo_bothEnqueued() {
        QueuedPr prA = new QueuedPr(802, "casehubio/devtown", "sha-802a", "alice",
            0.8, PriorityLane.NORMAL, Instant.now(), Set.of());
        QueuedPr prB = new QueuedPr(802, "casehubio/engine", "sha-802b", "alice",
            0.8, PriorityLane.NORMAL, Instant.now(), Set.of());

        store.enqueue(prA, UUID.randomUUID());
        store.enqueue(prB, UUID.randomUUID());

        List<QueueEntry> queued = store.queued();
        assertThat(queued).hasSize(2);
    }

    @Test
    void enqueue_afterDequeue_allowsReEnqueue() {
        QueuedPr pr = new QueuedPr(803, "casehubio/devtown", "sha-803", "bob",
            0.6, PriorityLane.NORMAL, Instant.now(), Set.of());

        store.enqueue(pr, UUID.randomUUID());
        store.dequeue(803, "casehubio/devtown");

        // DEQUEUED state should allow re-enqueue
        UUID newWorkItemId = UUID.randomUUID();
        store.enqueue(pr, newWorkItemId);

        List<QueueEntry> queued = store.queued();
        assertThat(queued).hasSize(1);
        assertThat(queued.get(0).workItemId()).isEqualTo(newWorkItemId);
    }

    @Test
    void enqueue_whileInBatch_silentlyIgnored() {
        QueuedPr pr = new QueuedPr(804, "casehubio/devtown", "sha-804", "carol",
            0.7, PriorityLane.NORMAL, Instant.now(), Set.of());
        UUID workItemId = UUID.randomUUID();

        store.enqueue(pr, workItemId);
        store.markInBatch(List.of(804), "casehubio/devtown", "batch-idem-test");

        // Attempt to re-enqueue while IN_BATCH — should be no-op
        store.enqueue(pr, UUID.randomUUID());

        // Entry should still be IN_BATCH, not duplicated
        List<QueueEntry> batchEntries = store.findEntriesByBatchId("batch-idem-test");
        assertThat(batchEntries).hasSize(1);
        assertThat(batchEntries.get(0).workItemId()).isEqualTo(workItemId);
    }
}
