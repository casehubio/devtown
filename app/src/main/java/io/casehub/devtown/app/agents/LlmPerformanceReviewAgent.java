package io.casehub.devtown.app.agents;

import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.review.LlmReviewerAgent;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.platform.agent.AgentProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LlmPerformanceReviewAgent extends LlmReviewerAgent {

    @Inject AgentProvider agentProvider;
    @Inject AgentRegistry agentRegistry;

    @Override protected AgentProvider agentProvider() { return agentProvider; }
    @Override protected AgentRegistry agentRegistry() { return agentRegistry; }
    @Override public String capability() { return ReviewDomain.PERFORMANCE_ANALYSIS; }
    @Override protected String agentId() { return "performance-reviewer"; }

    @Override
    protected String systemPromptBody() {
        return """
            You are a performance reviewer. Analyze the code diff for:
            - N+1 query patterns
            - Unbounded loops or recursive calls
            - Memory allocation issues (large objects in loops)
            - Missing pagination on collection operations
            - Blocking calls in async contexts
            Rate each finding by severity and your confidence level.""";
    }
}
