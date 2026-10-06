package io.casehub.devtown.app;

import io.casehub.devtown.domain.ReviewFinding;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PrReviewCaseHubSerializationTest {

    @Test
    void serializationIncludesLineRange() {
        var finding = new ReviewFinding(
            ReviewFinding.Severity.HIGH, "injection", "src/Api.java",
            new ReviewFinding.LineRange(42, 45), "SQL injection", 0.92);

        var result = serializeFinding(finding);

        assertEquals(42, result.get("startLine"));
        assertEquals(45, result.get("endLine"));
        assertEquals("HIGH", result.get("severity"));
        assertEquals("src/Api.java", result.get("filePath"));
    }

    @Test
    void serializationHandlesNullLineRange() {
        var finding = new ReviewFinding(
            ReviewFinding.Severity.LOW, "naming", "src/Foo.java",
            null, "bad name", 0.6);

        var result = serializeFinding(finding);

        assertNull(result.get("startLine"));
        assertNull(result.get("endLine"));
    }

    private Map<String, Object> serializeFinding(ReviewFinding f) {
        var m = new LinkedHashMap<String, Object>();
        m.put("severity", f.severity().name());
        m.put("category", f.category());
        m.put("filePath", f.filePath());
        m.put("message", f.message());
        m.put("confidence", f.confidence());
        m.put("startLine", f.lineRange() != null ? f.lineRange().startLine() : null);
        m.put("endLine", f.lineRange() != null ? f.lineRange().endLine() : null);
        return m;
    }
}
