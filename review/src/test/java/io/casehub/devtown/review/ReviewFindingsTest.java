package io.casehub.devtown.review;

import io.casehub.devtown.domain.ReviewFinding;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReviewFindingsTest {

    @Test
    void nullFindingsNormalisedToEmptyList() {
        var rf = new ReviewFindings(null);
        assertNotNull(rf.findings());
        assertTrue(rf.findings().isEmpty());
    }

    @Test
    void findingsListIsImmutable() {
        var finding = new ReviewFinding(
            ReviewFinding.Severity.LOW, "naming", "src/A.java",
            null, "bad name", 0.5);
        var rf = new ReviewFindings(List.of(finding));
        assertThrows(UnsupportedOperationException.class,
            () -> rf.findings().add(finding));
    }
}
