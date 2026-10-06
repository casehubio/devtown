package io.casehub.devtown.app.agents;

import io.casehub.devtown.review.PrDiff;
import io.casehub.devtown.review.PrPayload;
import io.casehub.devtown.review.ReviewContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodeAnalysisAgentStubTest {

    private final CodeAnalysisAgentStub agent = new CodeAnalysisAgentStub();

    @Test
    void securitySensitiveWhenAuthPathsPresent() {
        var diff = diffWith(
            fileDiff("src/auth/TokenService.java", "+    public String issueToken() {"),
            fileDiff("src/util/StringUtils.java", "+    return s.trim();")
        );
        var result = agent.analyse(new ReviewContext(samplePr(), diff));

        assertTrue(result.securitySensitive());
        assertTrue(result.flaggedFiles().contains("src/auth/TokenService.java"));
    }

    @Test
    void notSecuritySensitiveForSimpleRename() {
        var diff = diffWith(
            fileDiff("src/service/UserService.java", "+    private String name;"),
            fileDiff("src/util/Naming.java", "+    return camelCase(s);")
        );
        var result = agent.analyse(new ReviewContext(samplePr(), diff));

        assertFalse(result.securitySensitive());
        assertTrue(result.flaggedFiles().isEmpty());
    }

    @Test
    void architectureCrossingWhenThreePlusModules() {
        var diff = diffWith(
            fileDiff("src/payment/PaymentService.java", "+    pay();"),
            fileDiff("src/order/OrderService.java", "+    order();"),
            fileDiff("src/config/AppConfig.java", "+    config();"),
            fileDiff("src/auth/Auth.java", "+    auth();")
        );
        var result = agent.analyse(new ReviewContext(samplePr(), diff));

        assertTrue(result.architectureCrossing());
        assertFalse(result.crossingPoints().isEmpty());
    }

    @Test
    void scopeClassification() {
        var small = diffWith(fileDiff("src/A.java", "+x", 10, 5));
        assertEquals("small", agent.analyse(new ReviewContext(samplePr(), small)).scope());

        var large = diffWith(fileDiff("src/B.java", "+x", 800, 200));
        assertEquals("large", agent.analyse(new ReviewContext(samplePr(), large)).scope());
    }

    @Test
    void nullDiffReturnsDefaults() {
        var result = agent.analyse(new ReviewContext(samplePr(), null));

        assertTrue(result.complete());
        assertFalse(result.securitySensitive());
    }

    private static PrPayload samplePr() {
        return new PrPayload("org/repo", 1, "abc", "main", 100, "dev", 1L, List.of());
    }

    private static PrDiff diffWith(PrDiff.FileDiff... files) {
        return new PrDiff("org/repo", 1, "base", "head", List.of(files), false);
    }

    private static PrDiff.FileDiff fileDiff(String path, String patch) {
        return new PrDiff.FileDiff(path, "modified", "@@ -1,3 +1,5 @@\n" + patch, 2, 0);
    }

    private static PrDiff.FileDiff fileDiff(String path, String patch, int additions, int deletions) {
        return new PrDiff.FileDiff(path, "modified", "@@ -1,3 +1,5 @@\n" + patch, additions, deletions);
    }
}
