package io.casehub.devtown.app;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class CoordinatedChangeTrackerTest {

    CoordinatedChangeTracker tracker;
    UUID parentId = UUID.randomUUID();
    UUID reviewA = UUID.randomUUID();
    UUID reviewB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tracker = new CoordinatedChangeTracker();
        tracker.register(parentId, "casehubio/engine", reviewA);
        tracker.register(parentId, "casehubio/platform", reviewB);
    }

    @Test
    void findByReviewCaseId_returnsEntry() {
        var entry = tracker.findByReviewCaseId(reviewA);
        assertThat(entry).isNotNull();
        assertThat(entry.parentCaseId()).isEqualTo(parentId);
        assertThat(entry.repo()).isEqualTo("casehubio/engine");
    }

    @Test
    void findByReviewCaseId_unknownReturnsNull() {
        assertThat(tracker.findByReviewCaseId(UUID.randomUUID())).isNull();
    }

    @Test
    void findReviewCaseIds_returnsAll() {
        assertThat(tracker.findReviewCaseIds(parentId)).containsExactlyInAnyOrder(reviewA, reviewB);
    }

    @Test
    void isPartOfCoordinatedChange() {
        assertThat(tracker.isPartOfCoordinatedChange(reviewA)).isTrue();
        assertThat(tracker.isPartOfCoordinatedChange(UUID.randomUUID())).isFalse();
    }

    @Test
    void markCompleted_tracksPerRepo() {
        assertThat(tracker.markCompleted(parentId, "casehubio/engine")).isTrue();
        assertThat(tracker.markCompleted(parentId, "casehubio/engine")).isFalse();
    }

    @Test
    void tryTransitionToAllCompleted_atomicOnce() {
        tracker.markCompleted(parentId, "casehubio/engine");
        assertThat(tracker.tryTransitionToAllCompleted(parentId)).isFalse();

        tracker.markCompleted(parentId, "casehubio/platform");
        assertThat(tracker.tryTransitionToAllCompleted(parentId)).isTrue();
        assertThat(tracker.tryTransitionToAllCompleted(parentId)).isFalse();
    }

    @Test
    void parentTerminal_preventsSignaling() {
        assertThat(tracker.isParentTerminal(parentId)).isFalse();
        tracker.markParentTerminal(parentId);
        assertThat(tracker.isParentTerminal(parentId)).isTrue();
    }

    @Test
    void concurrentCompletion_onlyOneTransitions() throws Exception {
        tracker.markCompleted(parentId, "casehubio/engine");
        tracker.markCompleted(parentId, "casehubio/platform");

        var results = new boolean[2];
        var t1 = new Thread(() -> results[0] = tracker.tryTransitionToAllCompleted(parentId));
        var t2 = new Thread(() -> results[1] = tracker.tryTransitionToAllCompleted(parentId));
        t1.start(); t2.start();
        t1.join(); t2.join();

        assertThat(results[0] ^ results[1]).as("exactly one thread should succeed").isTrue();
    }
}
