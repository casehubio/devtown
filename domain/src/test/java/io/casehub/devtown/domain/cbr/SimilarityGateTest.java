package io.casehub.devtown.domain.cbr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SimilarityGateTest {

    @Test
    void disabled_passesEverything() {
        var query = PrFeatureVector.from("repo-a", 1, "alice", 100, List.of());
        var candidate = PrFeatureVector.from("repo-b", 2, "bob", 1, List.of());
        assertThat(SimilarityGate.DISABLED.passes(query, candidate)).isFalse();
    }

    @Test
    void disabledWithSameRepo_passes() {
        var gate = new SimilarityGate(0, 0.0, true);
        var query = PrFeatureVector.from("repo", 1, "alice", 100, List.of());
        var candidate = PrFeatureVector.from("repo", 2, "bob", 1, List.of());
        assertThat(gate.passes(query, candidate)).isTrue();
    }

    @Test
    void sameRepoGate_rejectsDifferentRepo() {
        var gate = new SimilarityGate(0, 0.0, true);
        var query = PrFeatureVector.from("repo-a", 1, "alice", 100, List.of());
        var candidate = PrFeatureVector.from("repo-b", 2, "bob", 100, List.of());
        assertThat(gate.passes(query, candidate)).isFalse();
    }

    @Test
    void sameRepoDisabled_acceptsDifferentRepo() {
        var gate = new SimilarityGate(0, 0.0, false);
        var query = PrFeatureVector.from("repo-a", 1, "alice", 100, List.of());
        var candidate = PrFeatureVector.from("repo-b", 2, "bob", 100, List.of());
        assertThat(gate.passes(query, candidate)).isTrue();
    }

    @Test
    void minModuleOverlap_passes_whenEnoughOverlap() {
        var gate = new SimilarityGate(1, 0.0, false);
        var query = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java", "api/src/main/java/Bar.java"));
        var candidate = PrFeatureVector.from("repo", 2, "bob", 100,
            List.of("core/src/main/java/Baz.java"));
        assertThat(gate.passes(query, candidate)).isTrue();
    }

    @Test
    void minModuleOverlap_rejects_whenInsufficientOverlap() {
        var gate = new SimilarityGate(2, 0.0, false);
        var query = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java", "api/src/main/java/Bar.java"));
        var candidate = PrFeatureVector.from("repo", 2, "bob", 100,
            List.of("core/src/main/java/Baz.java", "web/src/main/ts/App.tsx"));
        assertThat(gate.passes(query, candidate)).isFalse();
    }

    @Test
    void minModuleOverlap_zero_doesNotFilter() {
        var gate = new SimilarityGate(0, 0.0, false);
        var query = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java"));
        var candidate = PrFeatureVector.from("repo", 2, "bob", 100,
            List.of("web/src/main/ts/App.tsx"));
        assertThat(gate.passes(query, candidate)).isTrue();
    }

    @Test
    void minChangeSizeRatio_passes_whenSimilarSize() {
        var gate = new SimilarityGate(0, 0.5, false);
        var query = PrFeatureVector.from("repo", 1, "alice", 100, List.of());
        var candidate = PrFeatureVector.from("repo", 2, "bob", 80, List.of());
        assertThat(gate.passes(query, candidate)).isTrue();
    }

    @Test
    void minChangeSizeRatio_rejects_whenTooFarApart() {
        var gate = new SimilarityGate(0, 0.5, false);
        var query = PrFeatureVector.from("repo", 1, "alice", 100, List.of());
        var candidate = PrFeatureVector.from("repo", 2, "bob", 5, List.of());
        assertThat(gate.passes(query, candidate)).isFalse();
    }

    @Test
    void minChangeSizeRatio_zero_doesNotFilter() {
        var gate = new SimilarityGate(0, 0.0, false);
        var query = PrFeatureVector.from("repo", 1, "alice", 1000, List.of());
        var candidate = PrFeatureVector.from("repo", 2, "bob", 1, List.of());
        assertThat(gate.passes(query, candidate)).isTrue();
    }

    @Test
    void multipleGates_allMustPass() {
        var gate = new SimilarityGate(1, 0.3, true);
        var query = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java"));
        var candidate = PrFeatureVector.from("repo", 2, "bob", 80,
            List.of("core/src/main/java/Bar.java"));
        assertThat(gate.passes(query, candidate)).isTrue();
    }

    @Test
    void multipleGates_oneFailsRejects() {
        var gate = new SimilarityGate(1, 0.3, true);
        var query = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java"));
        var candidate = PrFeatureVector.from("different-repo", 2, "bob", 80,
            List.of("core/src/main/java/Bar.java"));
        assertThat(gate.passes(query, candidate)).isFalse();
    }
}
