package io.casehub.devtown.app.ledger;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.HashMap;
import java.util.Map;

public class TokenisationEnabledTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        var config = new HashMap<>(new LedgerEnabledTestProfile().getConfigOverrides());
        config.put("casehub.ledger.identity.tokenisation.enabled", "true");
        return config;
    }
}
