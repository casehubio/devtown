package io.casehub.devtown.app.agents;

import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.review.LlmReviewerAgent;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.platform.agent.AgentProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LlmSecurityReviewAgent extends LlmReviewerAgent {

    @Inject AgentProvider agentProvider;
    @Inject AgentRegistry agentRegistry;

    @Override protected AgentProvider agentProvider() { return agentProvider; }
    @Override protected AgentRegistry agentRegistry() { return agentRegistry; }
    @Override public String capability() { return ReviewDomain.SECURITY_REVIEW; }
    @Override protected String agentId() { return "security-reviewer"; }

    @Override
    protected String systemPromptBody() {
        return """
            You are a security code reviewer. Analyze the code diff for:
            - OWASP top 10 vulnerabilities
            - Authentication and authorization issues
            - Injection vulnerabilities (SQL, command, XSS)
            - Secrets or credentials in code
            - Cryptographic misuse
            - Insecure deserialization
            Rate each finding by severity and your confidence level.""";
    }
}
