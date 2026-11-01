package io.casehub.devtown.app.spi;

import io.casehub.api.spi.WorkerFunctionProvider;
import io.casehub.engine.flow.PlaybookWorkerFunctionProvider;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PlaybookProviderWiringTest {

    @Inject Instance<WorkerFunctionProvider> providers;

    @Test
    void playbookWorkerFunctionProviderIsRegistered() {
        boolean found = providers.stream()
            .anyMatch(p -> p instanceof PlaybookWorkerFunctionProvider);
        assertTrue(found,
            "PlaybookWorkerFunctionProvider should be registered as a WorkerFunctionProvider bean");
    }
}
