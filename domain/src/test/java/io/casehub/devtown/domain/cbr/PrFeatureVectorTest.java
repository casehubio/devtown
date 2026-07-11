package io.casehub.devtown.domain.cbr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PrFeatureVectorTest {

    @Test
    void extractsModulesFromPaths() {
        var v = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java", "core/src/test/java/FooTest.java",
                     "api/src/main/java/Bar.java"));
        assertThat(v.modules()).containsExactlyInAnyOrder("core", "api");
    }

    @Test
    void extractsLanguagesFromExtensions() {
        var v = PrFeatureVector.from("repo", 1, "alice", 50,
            List.of("src/main/java/Foo.java", "src/main/kotlin/Bar.kt",
                     "web/src/App.tsx", "scripts/deploy.sh"));
        assertThat(v.languages()).containsExactlyInAnyOrder("java", "kotlin", "typescript", "shell");
    }

    @Test
    void ktsExtensionMapsToKotlin() {
        var v = PrFeatureVector.from("repo", 1, "alice", 10,
            List.of("build.gradle.kts"));
        assertThat(v.languages()).containsExactly("kotlin");
    }

    @Test
    void configExtensionsMapToConfig() {
        var v = PrFeatureVector.from("repo", 1, "alice", 10,
            List.of("src/main/resources/app.yaml", "config.json"));
        assertThat(v.languages()).containsExactly("config");
    }

    @Test
    void noExtensionFilesIgnored() {
        var v = PrFeatureVector.from("repo", 1, "alice", 10,
            List.of("Makefile", "Dockerfile", "README"));
        assertThat(v.languages()).isEmpty();
    }

    @Test
    void detectsTestPaths() {
        assertThat(PrFeatureVector.from("r", 1, "a", 10,
            List.of("src/test/java/FooTest.java")).hasTests()).isTrue();
        assertThat(PrFeatureVector.from("r", 1, "a", 10,
            List.of("src/__tests__/foo.test.ts")).hasTests()).isTrue();
        assertThat(PrFeatureVector.from("r", 1, "a", 10,
            List.of("tests/test_foo.py")).hasTests()).isTrue();
        assertThat(PrFeatureVector.from("r", 1, "a", 10,
            List.of("pkg/handler_test.go")).hasTests()).isTrue();
        assertThat(PrFeatureVector.from("r", 1, "a", 10,
            List.of("src/main/java/Foo.java")).hasTests()).isFalse();
    }

    @Test
    void detectsTestPatternSpecTs() {
        assertThat(PrFeatureVector.from("r", 1, "a", 10,
            List.of("src/app.spec.ts")).hasTests()).isTrue();
        assertThat(PrFeatureVector.from("r", 1, "a", 10,
            List.of("src/app.spec.tsx")).hasTests()).isTrue();
        assertThat(PrFeatureVector.from("r", 1, "a", 10,
            List.of("src/app.test.tsx")).hasTests()).isTrue();
    }

    @Test
    void detectsTouchedConfigs() {
        assertThat(PrFeatureVector.from("r", 1, "a", 10,
            List.of("pom.xml")).touchedConfigs()).isTrue();
        assertThat(PrFeatureVector.from("r", 1, "a", 10,
            List.of("Dockerfile")).touchedConfigs()).isTrue();
        assertThat(PrFeatureVector.from("r", 1, "a", 10,
            List.of(".github/workflows/ci.yml")).touchedConfigs()).isTrue();
        assertThat(PrFeatureVector.from("r", 1, "a", 10,
            List.of("package.json")).touchedConfigs()).isTrue();
        assertThat(PrFeatureVector.from("r", 1, "a", 10,
            List.of("pyproject.toml")).touchedConfigs()).isTrue();
        assertThat(PrFeatureVector.from("r", 1, "a", 10,
            List.of("go.mod")).touchedConfigs()).isTrue();
        assertThat(PrFeatureVector.from("r", 1, "a", 10,
            List.of("src/main/java/Foo.java")).touchedConfigs()).isFalse();
    }

    @Test
    void emptyPathList() {
        var v = PrFeatureVector.from("repo", 1, "alice", 0, List.of());
        assertThat(v.changedPaths()).isEmpty();
        assertThat(v.modules()).isEmpty();
        assertThat(v.languages()).isEmpty();
        assertThat(v.hasTests()).isFalse();
        assertThat(v.touchedConfigs()).isFalse();
    }

    @Test
    void singleFileAtRoot() {
        var v = PrFeatureVector.from("repo", 1, "alice", 5,
            List.of("README.md"));
        assertThat(v.modules()).containsExactly("(root)");
    }

    @Test
    void preservesScalarFields() {
        var v = PrFeatureVector.from("casehubio/devtown", 42, "bob", 250,
            List.of("core/src/main/java/Foo.java"));
        assertThat(v.repo()).isEqualTo("casehubio/devtown");
        assertThat(v.prNumber()).isEqualTo(42);
        assertThat(v.contributor()).isEqualTo("bob");
        assertThat(v.linesChanged()).isEqualTo(250);
    }

    @Test
    void attributeRoundTrip() {
        var original = PrFeatureVector.from("casehubio/devtown", 42, "bob", 250,
            List.of("core/src/main/java/Foo.java", "core/src/test/java/FooTest.java",
                     "api/src/main/java/Bar.kt", "pom.xml"));
        var restored = PrFeatureVector.fromAttributes(original.toAttributes());
        assertThat(restored).isEqualTo(original);
    }

    @Test
    void attributeRoundTripWithCommasInPaths() {
        var original = PrFeatureVector.from("repo", 1, "alice", 10,
            List.of("src/main/java/Some,File.java"));
        var restored = PrFeatureVector.fromAttributes(original.toAttributes());
        assertThat(restored.changedPaths()).isEqualTo(original.changedPaths());
    }

    @Test
    void attributeRoundTripEmpty() {
        var original = PrFeatureVector.from("repo", 1, "alice", 0, List.of());
        var restored = PrFeatureVector.fromAttributes(original.toAttributes());
        assertThat(restored).isEqualTo(original);
    }

    @Test
    void changedPathsAreImmutable() {
        var paths = new java.util.ArrayList<>(List.of("src/main/java/Foo.java"));
        var v = PrFeatureVector.from("repo", 1, "alice", 10, paths);
        paths.add("src/main/java/Bar.java");
        assertThat(v.changedPaths()).hasSize(1);
    }
}
