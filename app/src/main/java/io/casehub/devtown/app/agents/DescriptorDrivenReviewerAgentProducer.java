package io.casehub.devtown.app.agents;

import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.review.DescriptorDrivenReviewerAgent;
import io.casehub.devtown.review.ReviewerAgent;
import io.casehub.devtown.review.ReviewerAgentRegistry;
import io.casehub.eidos.api.AgentMatch;
import io.casehub.eidos.api.AgentQuery;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.platform.agent.AgentProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class DescriptorDrivenReviewerAgentProducer {

    @Inject AgentProvider agentProvider;
    @Inject AgentRegistry agentRegistry;

    @Produces
    @ApplicationScoped
    ReviewerAgentRegistry produceRegistry() {
        List<ReviewerAgent> agents = new ArrayList<>();
        List<AgentMatch> matches = agentRegistry.find(
            AgentQuery.bySlot("reviewer", "default"));

        for (AgentMatch match : matches) {
            var descriptor = match.descriptor();
            for (var capability : descriptor.capabilities()) {
                if (ReviewDomain.FINDINGS_CAPABILITIES.contains(capability.name())) {
                    agents.add(new DescriptorDrivenReviewerAgent(
                        agentProvider, agentRegistry,
                        descriptor.agentId(), capability.name()));
                }
            }
        }
        return ReviewerAgentRegistry.of(agents);
    }
}
