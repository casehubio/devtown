package io.casehub.devtown.app.github;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class WriteBackEnabledTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("devtown.github.write-back.enabled", "true");
    }
}
