package io.casehub.devtown.domain;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class CoordinatedMergeResultTest {

    @Test
    void successCarriesMergeSha() {
        var result = new CoordinatedMergeResult.Success("casehubio/engine", "abc123");
        assertThat(result.repo()).isEqualTo("casehubio/engine");
        assertThat(result.mergeSha()).isEqualTo("abc123");
        assertThat(result).isInstanceOf(CoordinatedMergeResult.class);
    }

    @Test
    void failureCarriesReason() {
        var result = new CoordinatedMergeResult.Failure("casehubio/platform", "merge conflict");
        assertThat(result.repo()).isEqualTo("casehubio/platform");
        assertThat(result.reason()).isEqualTo("merge conflict");
        assertThat(result).isInstanceOf(CoordinatedMergeResult.class);
    }
}
