package io.casehub.devtown.domain.memory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModulePathNormalizerTest {

    @Test
    void appMainJavaFileNormalizesToApp() {
        var result = ModulePathNormalizer.normalize(List.of("app/src/main/java/io/casehub/Foo.java"));
        assertThat(result).containsExactly("app");
    }

    @Test
    void appTestJavaFileNormalizesToApp() {
        var result = ModulePathNormalizer.normalize(List.of("app/src/test/java/io/casehub/FooTest.java"));
        assertThat(result).containsExactly("app");
    }

    @Test
    void srcMainAndSrcTestInSameModuleDeduplicates() {
        var result = ModulePathNormalizer.normalize(List.of(
            "app/src/main/java/io/casehub/Foo.java",
            "app/src/test/java/io/casehub/FooTest.java"
        ));
        assertThat(result).containsExactly("app");
    }

    @Test
    void multipleModulesProducesDistinctEntries() {
        var result = ModulePathNormalizer.normalize(List.of(
            "app/src/main/java/io/casehub/Foo.java",
            "review/src/main/java/io/casehub/Bar.java"
        ));
        assertThat(result).containsExactlyInAnyOrder("app", "review");
    }

    @Test
    void multipleFilesInSameModuleDeduplicates() {
        var result = ModulePathNormalizer.normalize(List.of(
            "app/src/main/java/io/casehub/Foo.java",
            "app/src/main/java/io/casehub/Bar.java",
            "app/src/main/java/io/casehub/Baz.java"
        ));
        assertThat(result).containsExactly("app");
    }

    @Test
    void pomXmlNormalizesToRoot() {
        var result = ModulePathNormalizer.normalize(List.of("pom.xml"));
        assertThat(result).containsExactly(ModulePathNormalizer.ROOT);
    }

    @Test
    void readmeMdNormalizesToRoot() {
        var result = ModulePathNormalizer.normalize(List.of("README.md"));
        assertThat(result).containsExactly(ModulePathNormalizer.ROOT);
    }

    @Test
    void configApplicationYamlNormalizesToRoot() {
        var result = ModulePathNormalizer.normalize(List.of("config/application.yaml"));
        assertThat(result).containsExactly(ModulePathNormalizer.ROOT);
    }

    @Test
    void githubWorkflowsNormalizesToRoot() {
        var result = ModulePathNormalizer.normalize(List.of(".github/workflows/ci.yml"));
        assertThat(result).containsExactly(ModulePathNormalizer.ROOT);
    }

    @Test
    void mixedRootAndModuleProducesBoth() {
        var result = ModulePathNormalizer.normalize(List.of(
            "pom.xml",
            "app/src/main/java/io/casehub/Foo.java"
        ));
        assertThat(result).containsExactlyInAnyOrder(ModulePathNormalizer.ROOT, "app");
    }

    @Test
    void emptyListProducesEmpty() {
        var result = ModulePathNormalizer.normalize(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void reviewModuleNormalizesToReview() {
        var result = ModulePathNormalizer.normalize(List.of("review/src/main/java/io/casehub/PrPayload.java"));
        assertThat(result).containsExactly("review");
    }

    @Test
    void rootConstantIsCorrect() {
        assertThat(ModulePathNormalizer.ROOT).isEqualTo("(root)");
    }
}
