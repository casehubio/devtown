package io.casehub.devtown.review;

import io.casehub.devtown.domain.ReviewFinding;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LlmReviewerAgentTest {

    @Test
    void producesStructuredFindings() {
        var json = """
                   {"findings": [{"severity": "HIGH", "category": "injection",
                    "filePath": "src/Api.java",
                    "lineRange": {"startLine": 42, "endLine": 45},
                    "message": "SQL injection via string concat",
                    "confidence": 0.92}]}""";

        var agent   = createTestAgent(json);
        var diff    = sampleDiff("src/Api.java", "+ String sql = \"SELECT * FROM users WHERE id=\" + id;");
        var context = new ReviewContext(samplePr(), diff);

        var outcome = agent.handle(context);

        assertInstanceOf(ReviewerOutcome.Completed.class, outcome);
        var completed = (ReviewerOutcome.Completed) outcome;
        assertEquals(1, completed.findings().size());
        assertEquals(ReviewFinding.Severity.HIGH, completed.findings().get(0).severity());
        assertEquals("src/Api.java", completed.findings().get(0).filePath());
    }

    @Test
    void declinesWhenNoReviewableFiles() {
        var agent = createTestAgent("{}");
        var diff = new PrDiff("org/repo", 42, "base", "head",
                              List.of(new PrDiff.FileDiff("image.png", "modified", null, 0, 0)),
                              false);
        var context = new ReviewContext(samplePr(), diff);

        var outcome = agent.handle(context);
        assertInstanceOf(ReviewerOutcome.Declined.class, outcome);
    }

    @Test
    void filtersHallucinatedFilePaths() {
        var json = """
                   {"findings": [
                     {"severity": "HIGH", "category": "injection",
                      "filePath": "src/Api.java", "lineRange": null,
                      "message": "real finding", "confidence": 0.9},
                     {"severity": "LOW", "category": "naming",
                      "filePath": "src/NotInDiff.java", "lineRange": null,
                      "message": "hallucinated file", "confidence": 0.5}
                   ]}""";

        var agent   = createTestAgent(json);
        var diff    = sampleDiff("src/Api.java", "+ code");
        var context = new ReviewContext(samplePr(), diff);

        var outcome = agent.handle(context);
        assertInstanceOf(ReviewerOutcome.Completed.class, outcome);
        var findings = ((ReviewerOutcome.Completed) outcome).findings();
        assertEquals(1, findings.size());
        assertEquals("src/Api.java", findings.get(0).filePath());
    }

    @Test
    void returnsFailedWhenAllBatchesFail() {
        AgentProvider failingProvider = new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().failure(new RuntimeException("API down"));
            }

            @Override
            public io.casehub.platform.agent.AgentSession openSession(io.casehub.platform.agent.AgentSessionInit init) {
                throw new UnsupportedOperationException();
            }
        };

        var agent   = new TestLlmReviewerAgent(failingProvider);
        var diff    = sampleDiff("src/A.java", "+ code");
        var context = new ReviewContext(samplePr(), diff);

        var outcome = agent.handle(context);
        assertInstanceOf(ReviewerOutcome.Failed.class, outcome);
    }

    @Test
    void priorityIsOne() {
        var agent = createTestAgent("{}");
        assertEquals(1, agent.priority());
    }

    @Test
    void returnsFailedWhenDiffIsNull() {
        var agent   = createTestAgent("{}");
        var context = new ReviewContext(samplePr(), null);

        var outcome = agent.handle(context);
        assertInstanceOf(ReviewerOutcome.Failed.class, outcome);
    }


    private LlmReviewerAgent createTestAgent(String jsonResponse) {
        AgentProvider mockProvider = new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().items(
                        new AgentEvent.TextDelta(jsonResponse),
                        new AgentEvent.InvocationComplete(0, 0, 0, 0, 0, null, 100L, 80L, null, 1, false));
            }

            @Override
            public io.casehub.platform.agent.AgentSession openSession(io.casehub.platform.agent.AgentSessionInit init) {
                throw new UnsupportedOperationException();
            }
        };
        return new TestLlmReviewerAgent(mockProvider);
    }

    private PrDiff sampleDiff(String path, String patch) {
        return new PrDiff("org/repo", 42, "base", "head",
                          List.of(new PrDiff.FileDiff(path, "modified", patch, 1, 0)),
                          false);
    }

    private PrPayload samplePr() {
        return new PrPayload("org/repo", 42, "head", "main",
                             50, "alice", 1L, List.of("src/Api.java"));
    }

    static class TestLlmReviewerAgent extends LlmReviewerAgent {
        private final AgentProvider provider;

        TestLlmReviewerAgent(AgentProvider provider) {
            this.provider = provider;
        }

        @Override
        protected AgentProvider agentProvider() {return provider;}

        @Override
        protected AgentRegistry agentRegistry() {return null;}

        @Override
        public String capability()              {return "test-review";}

        @Override
        protected String agentId()              {return "test-agent";}

        @Override
        protected String systemPromptBody() {
            return "You are a test reviewer.";
        }
    }
}
