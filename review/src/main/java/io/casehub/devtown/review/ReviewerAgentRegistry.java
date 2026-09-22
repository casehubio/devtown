package io.casehub.devtown.review;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class ReviewerAgentRegistry {

    private final Map<String, ReviewerAgent> agentsByCapability;

    @Inject
    public ReviewerAgentRegistry(Instance<ReviewerAgent> agents) {
        agentsByCapability = new HashMap<>();
        for (ReviewerAgent agent : agents) {
            agentsByCapability.merge(agent.capability(), agent,
                (existing, incoming) ->
                    incoming.priority() > existing.priority() ? incoming : existing);
        }
    }

    ReviewerAgentRegistry(Map<String, ReviewerAgent> agents) {
        this.agentsByCapability = new HashMap<>(agents);
    }

    public static ReviewerAgentRegistry of(List<ReviewerAgent> agents) {
        var map = new HashMap<String, ReviewerAgent>();
        for (ReviewerAgent agent : agents) {
            map.merge(agent.capability(), agent,
                (existing, incoming) ->
                    incoming.priority() > existing.priority() ? incoming : existing);
        }
        return new ReviewerAgentRegistry(map);
    }

    public Optional<ReviewerAgent> forCapability(String capability) {
        return Optional.ofNullable(agentsByCapability.get(capability));
    }

    public Collection<ReviewerAgent> all() {
        return agentsByCapability.values();
    }
}
