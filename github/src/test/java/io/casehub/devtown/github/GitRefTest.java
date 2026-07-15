package io.casehub.devtown.github;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class GitRefTest {

    @Test
    void shaExtractsFromNestedObject() {
        var ref = new GitRef("refs/heads/main", Map.of("sha", "abc123"));
        assertThat(ref.sha()).isEqualTo("abc123");
    }

    @Test
    void shaReturnsNullWhenObjectIsNotMap() {
        var ref = new GitRef("refs/heads/main", "not-a-map");
        assertThat(ref.sha()).isNull();
    }

    @Test
    void shaReturnsNullWhenObjectIsNull() {
        var ref = new GitRef("refs/heads/main", null);
        assertThat(ref.sha()).isNull();
    }

    @Test
    void shaReturnsNullWhenMapMissesShaKey() {
        var ref = new GitRef("refs/heads/main", Map.of("type", "commit"));
        assertThat(ref.sha()).isNull();
    }
}
