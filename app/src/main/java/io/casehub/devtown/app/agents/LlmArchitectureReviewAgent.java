package io.casehub.devtown.app.agents;

import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.review.LlmReviewerAgent;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.platform.agent.AgentProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LlmArchitectureReviewAgent extends LlmReviewerAgent {

    @Inject AgentProvider agentProvider;
    @Inject AgentRegistry agentRegistry;

    @Override protected AgentProvider agentProvider() { return agentProvider; }
    @Override protected AgentRegistry agentRegistry() { return agentRegistry; }
    @Override public String capability() { return ReviewDomain.ARCHITECTURE_REVIEW; }
    @Override protected String agentId() { return "architecture-reviewer"; }

    @Override
    protected String systemPromptBody() {
        return """
            You are an architecture reviewer. Analyze the code diff for:
            - Structural impact on module boundaries
            - Coupling between components
            - API surface changes (new endpoints, changed contracts)
            - Dependency direction violations
            - Layering violations
            Rate each finding by severity and your confidence level.""";
    }
}
