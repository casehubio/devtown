package io.casehub.devtown.review;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PrPayloadTest {

    @Test
    void fromContextMap_extractsAllFields() {
        var map = Map.<String, Object>of(
            "repo", "org/repo",
            "id", 42,
            "headSha", "abc123",
            "baseRef", "main",
            "linesChanged", 150,
            "contributor", "alice",
            "changedPaths", List.of("src/Main.java", "src/Test.java"));

        PrPayload pr = PrPayload.fromContextMap(map);

        assertEquals("org/repo", pr.repo());
        assertEquals(42, pr.prNumber());
        assertEquals("abc123", pr.headSha());
        assertEquals("main", pr.baseRef());
        assertEquals(150, pr.linesChanged());
        assertEquals("alice", pr.contributor());
        assertEquals(List.of("src/Main.java", "src/Test.java"), pr.changedPaths());
    }

    @Test
    void fromContextMap_handlesStringId() {
        var map = Map.<String, Object>of(
            "repo", "org/repo",
            "id", "99",
            "headSha", "sha",
            "baseRef", "main",
            "linesChanged", 10,
            "contributor", "bob",
            "changedPaths", List.of());

        PrPayload pr = PrPayload.fromContextMap(map);
        assertEquals(99, pr.prNumber());
    }
}
