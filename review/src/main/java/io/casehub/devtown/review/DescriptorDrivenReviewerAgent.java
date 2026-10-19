package io.casehub.devtown.review;

import io.casehub.eidos.api.AgentRegistry;
import io.casehub.platform.agent.AgentProvider;

public class DescriptorDrivenReviewerAgent extends LlmReviewerAgent {

    private final AgentProvider provider;
    private final AgentRegistry registry;
    private final String agentId;
    private final String capabilityName;

    public DescriptorDrivenReviewerAgent(
            AgentProvider provider, AgentRegistry registry,
            String agentId, String capabilityName) {
        this.provider = provider;
        this.registry = registry;
        this.agentId = agentId;
        this.capabilityName = capabilityName;
    }

    @Override
    protected AgentProvider agentProvider() { return provider; }

    @Override
    protected AgentRegistry agentRegistry() { return registry; }

    @Override
    public String capability() { return capabilityName; }

    @Override
    protected String agentId() { return agentId; }

    @Override
    protected String systemPromptBody() {
        String prompt = resolveSystemPrompt();
        if (prompt == null) {
            throw new IllegalStateException(
                "No systemPrompt in descriptor extensionData for "
                + agentId + "/" + capabilityName);
        }
        return prompt;
    }
}
