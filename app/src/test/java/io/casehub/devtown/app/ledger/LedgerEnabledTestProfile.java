package io.casehub.devtown.app.ledger;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class LedgerEnabledTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "casehub.ledger.enabled", "true",
            "quarkus.flyway.qhorus.migrate-at-start", "true",
            "quarkus.hibernate-orm.qhorus.database.generation", "none",
            "quarkus.datasource.qhorus.jdbc.url",
                "jdbc:h2:mem:devtown-ledger-test;MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE",
            // Override Flyway locations — exclude db/qhorus/migration to avoid V2000
            // collision between qhorus (V2000__agent_message_ledger_entry) and
            // engine-ledger (V2000__case_ledger_entry). Qhorus messaging tables are
            // created by a test-only Flyway migration in db/devtown-test/migration
            // with a non-conflicting version (V3000).
            "quarkus.flyway.qhorus.locations",
                "classpath:db/ledger/migration,classpath:db/engine-ledger/migration,classpath:db/devtown/migration,classpath:db/devtown-test/migration"
        );
    }
}
