package io.casehub.devtown.review;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ReviewerAgentRegistry {

    private final Map<String, ReviewerAgent> agentsByCapability;

    ReviewerAgentRegistry(Map<String, ReviewerAgent> agents) {
        this.agentsByCapability = new HashMap<>(agents);
    }

    public static ReviewerAgentRegistry of(List<ReviewerAgent> agents) {
        var map = new HashMap<String, ReviewerAgent>();
        for (ReviewerAgent agent : agents) {
            if (map.containsKey(agent.capability())) {
                throw new IllegalStateException(
                    "Duplicate agent for capability: " + agent.capability());
            }
            map.put(agent.capability(), agent);
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
