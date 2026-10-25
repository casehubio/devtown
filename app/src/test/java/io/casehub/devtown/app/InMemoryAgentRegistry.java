package io.casehub.devtown.app;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentMatch;
import io.casehub.eidos.api.AgentQuery;
import io.casehub.eidos.api.AgentRegistry;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Alternative
@Priority(10)
@ApplicationScoped
public class InMemoryAgentRegistry implements AgentRegistry {

    private final ConcurrentHashMap<String, AgentDescriptor> store = new ConcurrentHashMap<>();

    @Override
    public void register(AgentDescriptor descriptor) {
        store.put(key(descriptor.agentId(), descriptor.tenancyId()), descriptor);
    }

    @Override
    public Optional<AgentDescriptor> findById(String agentId, String tenancyId) {
        return Optional.ofNullable(store.get(key(agentId, tenancyId)));
    }

    @Override
    public List<AgentMatch> find(AgentQuery query) {
        return store.values().stream()
                .filter(d -> query.slot() == null || query.slot().equals(d.slot()))
                .filter(d -> d.tenancyId().equals(query.tenancyId()))
                .filter(d -> query.capabilityName() == null
                        || d.capabilities().stream()
                            .anyMatch(c -> c.name().equals(query.capabilityName())))
                .map(d -> new AgentMatch(d, null))
                .toList();
    }

    private static String key(String agentId, String tenancyId) {
        return agentId + ":" + tenancyId;
    }
}
