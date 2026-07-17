package io.casehub.devtown.domain;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import java.util.List;

class CoordinatedChangeRequestTest {

    @Test
    void requestHoldsRepoEntries() {
        var entry = new RepoChangeEntry("casehubio", "engine", 42, "abc123", "main", "alice", List.of("src/Main.java"), 100);
        var request = new CoordinatedChangeRequest(List.of(entry));
        assertThat(request.repos()).hasSize(1);
        assertThat(request.repos().get(0).owner()).isEqualTo("casehubio");
        assertThat(request.repos().get(0).repo()).isEqualTo("engine");
        assertThat(request.repos().get(0).prNumber()).isEqualTo(42);
    }
}
