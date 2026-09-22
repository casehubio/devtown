package io.casehub.devtown.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReviewFindingTest {

    @Test
    void confidenceClampedToUnitRange() {
        var finding = new ReviewFinding(
            ReviewFinding.Severity.HIGH, "injection", "src/Api.java",
            new ReviewFinding.LineRange(42, 45), "SQL injection", 1.5);
        assertEquals(1.0, finding.confidence());

        var low = new ReviewFinding(
            ReviewFinding.Severity.LOW, "naming", "src/Util.java",
            null, "inconsistent naming", -0.1);
        assertEquals(0.0, low.confidence());
    }

    @Test
    void lineRangeNullableForFileLevelFindings() {
        var finding = new ReviewFinding(
            ReviewFinding.Severity.INFO, "complexity", "src/Big.java",
            null, "file exceeds 500 lines", 0.7);
        assertNull(finding.lineRange());
    }

    @Test
    void normalConfidencePreserved() {
        var finding = new ReviewFinding(
            ReviewFinding.Severity.MEDIUM, "race-condition", "src/Cache.java",
            new ReviewFinding.LineRange(10, 20), "concurrent access", 0.85);
        assertEquals(0.85, finding.confidence(), 0.001);
    }
}
