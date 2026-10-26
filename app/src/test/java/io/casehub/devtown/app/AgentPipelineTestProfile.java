package io.casehub.devtown.app;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class AgentPipelineTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.ofEntries(
                Map.entry("quarkus.arc.exclude-types",
                          "io.casehub.agency.**,"
                          + "io.casehub.eidos.runtime.registry.jpa.JpaAgentRegistry,"
                          + "io.casehub.eidos.runtime.health.jpa.JpaDispositionSignalStore,"
                          + "io.casehub.eidos.runtime.health.jpa.JpaBehavioralSignalStore,"
                          + "io.casehub.eidos.runtime.health.jpa.JpaAgentStateStore,"
                          + "io.casehub.eidos.runtime.health.DefaultDispositionHealth,"
                          + "io.casehub.eidos.runtime.health.DefaultDispositionEvolution,"
                          + "io.casehub.eidos.runtime.health.DefaultGoalEvolution,"
                          + "io.casehub.eidos.runtime.health.DefaultCapabilityHealth,"
                          + "io.casehub.testing.TestCaseInstanceRepository,"
                          + "io.casehub.work.runtime.service.JpaWorkloadProvider,"
                          + "io.casehub.engine.actorstate.ActorStateResource,"
                          + "io.casehub.engine.actorstate.ReactiveActorStateResource,"
                          + "io.casehub.work.runtime.service.DeclarativeSlaBreachPolicy,"
                          + "io.casehub.work.engine.JpaPlanItemStore,"
                          + "io.casehub.qhorus.runtime.ledger.QhorusLedgerEntryRepository,"
                          + "io.casehub.qhorus.runtime.message.RoutingBridge,"
                          + "io.casehub.qhorus.runtime.message.DefaultObligorTrustPolicy,"
                          + "io.casehub.blackboard.handler.MilestoneAchievementHandler,"
                          + "io.casehub.connectors.signal.SignalConnector,"
                          + "io.casehub.connectors.twilio.TwilioSmsConnector,"
                          + "io.casehub.connectors.whatsapp.WhatsAppConnector,"
                          + "io.casehub.connectors.signal.cli.SignalClient,"
                          + "io.casehub.engine.runtime.callback.CallbackActionRiskClassifier,"
                          + "io.casehub.work.runtime.service.TenantScopedPrincipal,"
                          + "io.casehub.qhorus.runtime.identity.QhorusInboundCurrentPrincipal,"
                          + "io.casehub.qhorus.runtime.ledger.QhorusLedgerMerkleFrontierRepository,"
                          + "io.casehub.engine.runtime.engine.recovery.WorkerRecoveryCoordinator,"
                          + "io.casehub.engine.runtime.engine.recovery.WorkerRecoveryHealthCheck,"
                          + "io.casehub.work.runtime.preferences.WorkPreferenceRegistrar,"
                          + "io.casehub.devtown.app.spi.GitHubSecurityAugmentor,"
                          + "io.casehub.devtown.app.spi.GitHubGroupMembershipProvider,"
                          + "io.casehub.devtown.github.GitHubPrDiffClient,"
                          + "io.casehub.devtown.app.routing.DevtownTrustRoutingPolicyProvider,"
                          + "io.casehub.platform.agent.claude.quarkus.ClaudeBeans,"
                          + "io.casehub.platform.agent.ollama.quarkus.OllamaBeans"),

                // Index eidos modules for CDI discovery
                Map.entry("quarkus.index-dependency.eidos.group-id", "io.casehub"),
                Map.entry("quarkus.index-dependency.eidos.artifact-id", "casehub-eidos"),
                Map.entry("quarkus.index-dependency.eidos-core.group-id", "io.casehub"),
                Map.entry("quarkus.index-dependency.eidos-core.artifact-id", "casehub-eidos-core"),

                // Index agent-router modules for RoutingAgentProvider + BackendInstanceCoordinator
                Map.entry("quarkus.index-dependency.agent-router.group-id", "io.casehub"),
                Map.entry("quarkus.index-dependency.agent-router.artifact-id", "casehub-platform-agent-router"),
                Map.entry("quarkus.index-dependency.agent-router-core.group-id", "io.casehub"),
                Map.entry("quarkus.index-dependency.agent-router-core.artifact-id", "casehub-platform-agent-router-core"),

                // Index agent-config for ManifestLoader + alias resolution
                Map.entry("quarkus.index-dependency.agent-config.group-id", "io.casehub"),
                Map.entry("quarkus.index-dependency.agent-config.artifact-id", "casehub-platform-agent-config"),
                Map.entry("quarkus.index-dependency.agent-config-core.group-id", "io.casehub"),
                Map.entry("quarkus.index-dependency.agent-config-core.artifact-id", "casehub-platform-agent-config-core"),

                // Index simulation modules for SimulatedAgentBackend CDI producer
                Map.entry("quarkus.index-dependency.simulation-config.group-id", "io.casehub"),
                Map.entry("quarkus.index-dependency.simulation-config.artifact-id", "casehub-platform-simulation-config"),
                Map.entry("quarkus.index-dependency.simulation-core.group-id", "io.casehub"),
                Map.entry("quarkus.index-dependency.simulation-core.artifact-id", "casehub-platform-agent-simulation-core"),

                // Index agent-session-core for session pool support
                Map.entry("quarkus.index-dependency.agent-session-core.group-id", "io.casehub"),
                Map.entry("quarkus.index-dependency.agent-session-core.artifact-id", "casehub-platform-agent-session-core"),

                // Force simulation backend as default (TestManifestResultProducer overrides alias resolution)
                Map.entry("casehub.platform.agent.default-backend", "simulation")
        );
    }
}
