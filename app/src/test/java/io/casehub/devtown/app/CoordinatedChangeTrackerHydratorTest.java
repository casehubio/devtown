package io.casehub.devtown.app;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class CoordinatedChangeTrackerHydratorTest {

    CoordinatedChangeTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new CoordinatedChangeTracker();
    }

    @Test
    void hydrateFromContext_rebuildsTrackerState() {
        UUID parentId = UUID.randomUUID();
        UUID reviewA = UUID.randomUUID();
        UUID reviewB = UUID.randomUUID();

        var parentContext = Map.<String, Object>of(
            "repos", List.of(
                Map.of("owner", "casehubio", "repo", "engine"),
                Map.of("owner", "casehubio", "repo", "platform")
            ),
            "reviewCases", Map.of(
                "casehubio/engine", reviewA.toString(),
                "casehubio/platform", reviewB.toString()
            )
        );

        CoordinatedChangeTrackerHydrator.hydrateFromContext(tracker, parentId, parentContext);

        assertThat(tracker.findByReviewCaseId(reviewA)).isNotNull();
        assertThat(tracker.findByReviewCaseId(reviewA).repo()).isEqualTo("casehubio/engine");
        assertThat(tracker.findByReviewCaseId(reviewB)).isNotNull();
        assertThat(tracker.findByReviewCaseId(reviewB).repo()).isEqualTo("casehubio/platform");
        assertThat(tracker.findReviewCaseIds(parentId)).containsExactlyInAnyOrder(reviewA, reviewB);
    }

    @Test
    void hydrateFromContext_noReviewCases_doesNothing() {
        UUID parentId = UUID.randomUUID();
        var parentContext = Map.<String, Object>of("repos", List.of());

        CoordinatedChangeTrackerHydrator.hydrateFromContext(tracker, parentId, parentContext);

        assertThat(tracker.findReviewCaseIds(parentId)).isEmpty();
    }
}
