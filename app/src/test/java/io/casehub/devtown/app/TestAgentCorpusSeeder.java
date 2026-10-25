package io.casehub.devtown.app;

import io.casehub.platform.agent.simulation.AgentCorpus;
import io.casehub.platform.simulation.SimulationRuntime;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
class TestAgentCorpusSeeder {

    @Inject Instance<SimulationRuntime> runtime;

    void onStartup(@Observes StartupEvent event) {
        if (!runtime.isResolvable()) {
            return;
        }

        var seed = AgentCorpus.invoke("default");

        seed.add(
                AgentCorpus.input("", ""),
                AgentCorpus.textResponse(
                        "{\"findings\": [{\"message\": \"Potential token exposure in auth service\", "
                        + "\"severity\": \"MEDIUM\", \"confidence\": 0.85, "
                        + "\"category\": \"security\", "
                        + "\"filePath\": \"src/auth/TokenService.java\", "
                        + "\"lineRange\": {\"startLine\": 1, \"endLine\": 10}}]}"));

        runtime.get().apply(seed);
    }
}
