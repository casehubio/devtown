package io.casehub.devtown.app.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReviewDetailExtractionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void extractCapabilityFromHash_standardFormat() {
        var meta = MAPPER.createObjectNode()
                .put("inputDataHash", "caseId-123:code-analyzer:code-analysis:hashvalue");
        assertEquals("code-analysis",
                GovernanceQueryService.extractCapabilityFromHash(meta));
    }

    @Test
    void extractCapabilityFromHash_reviewerWorker() {
        var meta = MAPPER.createObjectNode()
                .put("inputDataHash", "caseId-123:reviewer-security-review:security-review:hashvalue");
        assertEquals("security-review",
                GovernanceQueryService.extractCapabilityFromHash(meta));
    }

    @Test
    void extractCapabilityFromHash_nullMetadata() {
        assertNull(GovernanceQueryService.extractCapabilityFromHash(null));
    }

    @Test
    void extractCapabilityFromHash_noInputDataHash() {
        var meta = MAPPER.createObjectNode().put("other", "value");
        assertNull(GovernanceQueryService.extractCapabilityFromHash(meta));
    }

    @Test
    void extractCapabilityFromHash_tooFewParts() {
        var meta = MAPPER.createObjectNode().put("inputDataHash", "only:two");
        assertNull(GovernanceQueryService.extractCapabilityFromHash(meta));
    }

    @Test
    void findingsExtractedFromPayloadNestedPath() {
        var payload = MAPPER.createObjectNode();
        var outcome = MAPPER.createObjectNode();
        var findings = MAPPER.createArrayNode();
        var finding = MAPPER.createObjectNode()
                .put("severity", "HIGH")
                .put("category", "credential-exposure")
                .put("filePath", "src/auth/Token.java")
                .put("message", "Hardcoded secret")
                .put("confidence", 0.9);
        finding.put("startLine", 42);
        finding.put("endLine", 45);
        findings.add(finding);
        outcome.set("findings", findings);
        outcome.put("outcome", "REJECTED");
        var securityReview = MAPPER.createObjectNode();
        securityReview.set("outcome", outcome);
        payload.set("securityReview", securityReview);

        var contextKey = GovernanceQueryService.CAPABILITY_CONTEXT_KEYS.get("security-review");
        assertEquals("securityReview", contextKey);

        var capNode = payload.path(contextKey).path("outcome");
        assertTrue(capNode.has("findings"));
        assertEquals(1, capNode.get("findings").size());
        assertEquals("HIGH", capNode.get("findings").get(0).get("severity").asText());
        assertEquals("src/auth/Token.java", capNode.get("findings").get(0).get("filePath").asText());
        assertEquals(42, capNode.get("findings").get(0).get("startLine").asInt());
    }

    @Test
    void featureVectorExtractedFromCodeAnalysisPayload() {
        var payload = MAPPER.createObjectNode();
        var codeAnalysis = MAPPER.createObjectNode()
                .put("complete", true)
                .put("securitySensitive", true)
                .put("architectureCrossing", false)
                .put("scope", "medium");
        var flaggedFiles = MAPPER.createArrayNode();
        flaggedFiles.add("src/auth/TokenService.java");
        flaggedFiles.add("src/auth/RBAC.java");
        codeAnalysis.set("flaggedFiles", flaggedFiles);
        payload.set("codeAnalysis", codeAnalysis);

        var output = payload.path("codeAnalysis");
        assertFalse(output.isMissingNode());
        assertTrue(output.get("securitySensitive").asBoolean());
        assertFalse(output.get("architectureCrossing").asBoolean());
        assertEquals("medium", output.get("scope").asText());
        assertEquals(2, output.get("flaggedFiles").size());
    }

    @Test
    void emptyFindingsNotAddedToMap() {
        var payload = MAPPER.createObjectNode();
        var styleCheck = MAPPER.createObjectNode();
        var outcome = MAPPER.createObjectNode();
        outcome.put("outcome", "APPROVED");
        outcome.set("findings", MAPPER.createArrayNode());
        styleCheck.set("outcome", outcome);
        payload.set("styleCheck", styleCheck);

        var capNode = payload.path("styleCheck").path("outcome");
        assertTrue(capNode.has("findings"));
        assertEquals(0, capNode.get("findings").size());
    }

    @Test
    void capabilityContextKeysMapComplete() {
        var keys = GovernanceQueryService.CAPABILITY_CONTEXT_KEYS;
        assertEquals("codeAnalysis", keys.get("code-analysis"));
        assertEquals("securityReview", keys.get("security-review"));
        assertEquals("architectureReview", keys.get("architecture-review"));
        assertEquals("styleCheck", keys.get("style-review"));
        assertEquals("testCoverage", keys.get("test-coverage"));
        assertEquals("performanceAnalysis", keys.get("performance-analysis"));
    }
}
