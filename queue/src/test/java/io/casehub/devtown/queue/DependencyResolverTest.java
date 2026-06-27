package io.casehub.devtown.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DependencyResolverTest {

    private static final Instant NOW = Instant.parse("2026-06-27T12:00:00Z");

    private QueuedPr pr(int number, Set<Integer> deps) {
        return new QueuedPr(number, "sha" + number, "alice", 0.7, PriorityLane.NORMAL, NOW, deps);
    }

    @Test
    void noDependencies_preservesOrder() {
        var prs = List.of(pr(1, Set.of()), pr(2, Set.of()), pr(3, Set.of()));
        var sorted = DependencyResolver.resolve(prs);
        assertThat(sorted).extracting(QueuedPr::number).containsExactly(1, 2, 3);
    }

    @Test
    void dependencyOrderedBeforeDependent() {
        var pr1 = pr(1, Set.of());
        var pr2 = pr(2, Set.of(1));
        var sorted = DependencyResolver.resolve(List.of(pr2, pr1));
        assertThat(sorted).extracting(QueuedPr::number).containsExactly(1, 2);
    }

    @Test
    void transitiveChain() {
        var pr1 = pr(1, Set.of());
        var pr2 = pr(2, Set.of(1));
        var pr3 = pr(3, Set.of(2));
        var sorted = DependencyResolver.resolve(List.of(pr3, pr1, pr2));
        assertThat(sorted).extracting(QueuedPr::number).containsExactly(1, 2, 3);
    }

    @Test
    void cycleThrowsIllegalStateException() {
        var pr1 = pr(1, Set.of(2));
        var pr2 = pr(2, Set.of(1));
        assertThatThrownBy(() -> DependencyResolver.resolve(List.of(pr1, pr2)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cycle");
    }

    @Test
    void dependencyOnPrNotInQueue_ignored() {
        var pr1 = pr(1, Set.of(99));
        var sorted = DependencyResolver.resolve(List.of(pr1));
        assertThat(sorted).extracting(QueuedPr::number).containsExactly(1);
    }
}
