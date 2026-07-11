package io.casehub.devtown.domain.cbr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeightedJaccardSimilarityTest {

    private static final double TOLERANCE = 0.0001;

    private final WeightedJaccardSimilarity defaultMetric =
        new WeightedJaccardSimilarity(1.0, 1.5, 0.5, 1.0, 0.5);

    @Test
    void identicalPrs_scorePerfect() {
        var a = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java"));
        assertThat(defaultMetric.compute(a, a).score()).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void completelyDisjointPrs_scoreZero() {
        var a = PrFeatureVector.from("repo-a", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java"));
        var b = PrFeatureVector.from("repo-b", 2, "bob", 1,
            List.of("web/src/main/ts/Bar.ts"));
        assertThat(defaultMetric.compute(a, b).score()).isCloseTo(0.0, within(0.05));
    }

    @Test
    void sameContributorDifferentFiles_contributorDimensionContributes() {
        var a = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java"));
        var b = PrFeatureVector.from("repo", 2, "alice", 1,
            List.of("web/src/main/ts/Bar.ts"));
        var score = defaultMetric.compute(a, b);
        assertThat(score.breakdown().get("contributor")).isCloseTo(1.0, within(TOLERANCE));
        assertThat(score.breakdown().get("file-paths")).isCloseTo(0.0, within(TOLERANCE));
        assertThat(score.score()).isGreaterThan(0.0);
    }

    @Test
    void changeSizeRatio_sameSize() {
        var a = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java"));
        var b = PrFeatureVector.from("repo", 2, "bob", 100,
            List.of("core/src/main/java/Foo.java"));
        assertThat(defaultMetric.compute(a, b).breakdown().get("change-size"))
            .isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void changeSizeRatio_halfSize() {
        var a = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java"));
        var b = PrFeatureVector.from("repo", 2, "bob", 200,
            List.of("core/src/main/java/Foo.java"));
        assertThat(defaultMetric.compute(a, b).breakdown().get("change-size"))
            .isCloseTo(0.5, within(TOLERANCE));
    }

    @Test
    void changeSizeRatio_bothZero() {
        var a = PrFeatureVector.from("repo", 1, "alice", 0,
            List.of("core/src/main/java/Foo.java"));
        var b = PrFeatureVector.from("repo", 2, "bob", 0,
            List.of("core/src/main/java/Foo.java"));
        assertThat(defaultMetric.compute(a, b).breakdown().get("change-size"))
            .isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void changeSizeRatio_extremeDisparity() {
        var a = PrFeatureVector.from("repo", 1, "alice", 1,
            List.of("core/src/main/java/Foo.java"));
        var b = PrFeatureVector.from("repo", 2, "bob", 1000,
            List.of("core/src/main/java/Foo.java"));
        assertThat(defaultMetric.compute(a, b).breakdown().get("change-size"))
            .isCloseTo(0.001, within(TOLERANCE));
    }

    @Test
    void zeroWeightDimension_excluded() {
        var metric = new WeightedJaccardSimilarity(0.0, 0.0, 0.0, 0.0, 1.0);
        var a = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java"));
        var b = PrFeatureVector.from("repo", 2, "alice", 1,
            List.of("web/src/main/ts/Bar.ts"));
        assertThat(metric.compute(a, b).score()).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void allWeightsZero_scoreZero() {
        var metric = new WeightedJaccardSimilarity(0.0, 0.0, 0.0, 0.0, 0.0);
        var a = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java"));
        assertThat(metric.compute(a, a).score()).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void breakdownContainsAllFiveDimensions() {
        var a = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java"));
        var b = PrFeatureVector.from("repo", 2, "bob", 200,
            List.of("core/src/main/java/Bar.java"));
        var breakdown = defaultMetric.compute(a, b).breakdown();
        assertThat(breakdown).containsKeys(
            "file-paths", "modules", "languages", "change-size", "contributor");
    }

    @Test
    void emptyPathSets_jaccardReturnsOne() {
        var a = PrFeatureVector.from("repo", 1, "alice", 100, List.of());
        var b = PrFeatureVector.from("repo", 2, "alice", 100, List.of());
        var score = defaultMetric.compute(a, b);
        assertThat(score.breakdown().get("file-paths")).isCloseTo(1.0, within(TOLERANCE));
        assertThat(score.breakdown().get("modules")).isCloseTo(1.0, within(TOLERANCE));
        assertThat(score.breakdown().get("languages")).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void singleSharedFile_correctJaccard() {
        var a = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java", "core/src/main/java/Bar.java",
                     "core/src/main/java/Baz.java"));
        var b = PrFeatureVector.from("repo", 2, "bob", 100,
            List.of("core/src/main/java/Foo.java", "api/src/main/java/Qux.java"));
        // |{Foo}| / |{Foo,Bar,Baz,Qux}| = 1/4 = 0.25
        assertThat(defaultMetric.compute(a, b).breakdown().get("file-paths"))
            .isCloseTo(0.25, within(TOLERANCE));
    }

    @Test
    void scoreIsComparable() {
        var low = new SimilarityScore(0.3, Map.of());
        var high = new SimilarityScore(0.8, Map.of());
        assertThat(high).isGreaterThan(low);
    }

    @Test
    void scoreIsComparable_descending() {
        var low = new SimilarityScore(0.3, Map.of());
        var high = new SimilarityScore(0.8, Map.of());
        assertThat(low).isLessThan(high);
    }

    @Test
    void modulesWeighedHigherThanPaths() {
        // Two PRs share modules but not files vs share files but not modules
        var query = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java", "api/src/main/java/Bar.java"));

        // Shares modules (core, api) but different files
        var sameModules = PrFeatureVector.from("repo", 2, "bob", 100,
            List.of("core/src/main/java/Qux.java", "api/src/main/java/Baz.java"));

        // Shares one file but only one module
        var sameFile = PrFeatureVector.from("repo", 3, "carol", 100,
            List.of("core/src/main/java/Foo.java"));

        var scoreModules = defaultMetric.compute(query, sameModules);
        var scoreFile = defaultMetric.compute(query, sameFile);

        assertThat(scoreModules.breakdown().get("modules"))
            .isGreaterThan(scoreFile.breakdown().get("modules"));
    }
}
