package io.casehub.devtown.app.agents;

import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.review.LlmReviewerAgent;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.platform.agent.AgentProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LlmStyleReviewAgent extends LlmReviewerAgent {

    @Inject AgentProvider agentProvider;
    @Inject AgentRegistry agentRegistry;

    @Override protected AgentProvider agentProvider() { return agentProvider; }
    @Override protected AgentRegistry agentRegistry() { return agentRegistry; }
    @Override public String capability() { return ReviewDomain.STYLE_REVIEW; }
    @Override protected String agentId() { return "style-reviewer"; }

    @Override
    protected String systemPromptBody() {
        return """
            You are a style reviewer. Analyze the code diff for:
            - Naming consistency (variables, methods, classes)
            - Idiomatic patterns for the language
            - Code formatting issues not caught by linters
            - Readability concerns
            Rate each finding by severity and your confidence level.""";
    }
}
