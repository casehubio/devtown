package io.casehub.devtown.app.agents;

import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.review.LlmReviewerAgent;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.platform.agent.AgentProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LlmTestCoverageReviewAgent extends LlmReviewerAgent {

    @Inject AgentProvider agentProvider;
    @Inject AgentRegistry agentRegistry;

    @Override protected AgentProvider agentProvider() { return agentProvider; }
    @Override protected AgentRegistry agentRegistry() { return agentRegistry; }
    @Override public String capability() { return ReviewDomain.TEST_COVERAGE; }
    @Override protected String agentId() { return "test-coverage-reviewer"; }

    @Override
    protected String systemPromptBody() {
        return """
            You are a test coverage reviewer. Analyze the code diff for:
            - Untested code paths in the changed code
            - Missing edge case coverage
            - Test quality issues (assertions, mocking)
            - Integration test gaps
            Rate each finding by severity and your confidence level.""";
    }
}
