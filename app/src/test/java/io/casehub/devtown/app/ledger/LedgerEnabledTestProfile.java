package io.casehub.devtown.app.ledger;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class LedgerEnabledTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.ofEntries(
                Map.entry("casehub.ledger.enabled", "true"),
                Map.entry("quarkus.flyway.qhorus.migrate-at-start", "true"),
                Map.entry("quarkus.flyway.qhorus.clean-at-start", "true"),
                Map.entry("quarkus.datasource.qhorus.jdbc.url",
                          "jdbc:h2:mem:devtown-ledger-test;MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE"),
                Map.entry("quarkus.flyway.qhorus.locations",
                          "classpath:db/ledger/migration,classpath:db/engine-ledger/migration,classpath:db/devtown/migration,classpath:db/devtown-test/migration"),
                Map.entry("quarkus.hibernate-orm.qhorus.schema-management.strategy", "update"),
                Map.entry("quarkus.arc.exclude-types",
                          "io.casehub.agency.**,io.casehub.eidos.**,"
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
                          + "io.casehub.platform.oidc.SecurityIdentityCurrentPrincipal,"
                          + "io.casehub.qhorus.runtime.identity.QhorusInboundCurrentPrincipal,"
                          + "io.casehub.qhorus.runtime.ledger.QhorusLedgerMerkleFrontierRepository,"
                          + "io.casehub.engine.runtime.engine.recovery.WorkerRecoveryCoordinator,"
                          + "io.casehub.engine.runtime.engine.recovery.WorkerRecoveryHealthCheck,"
                          + "io.casehub.work.runtime.preferences.WorkPreferenceRegistrar,"
                          + "io.casehub.devtown.app.InMemoryLedgerEntryRepository")
                            );
    }
}
