package io.casehub.devtown.app.spi;

import io.casehub.devtown.review.ReviewTriageHandler;
import io.casehub.platform.api.governance.TriageHandler;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ReviewTriageHandlerDisplacementTest {

    @Inject Instance<TriageHandler> triageHandler;

    @Test
    void triageHandlerIsResolvable() {
        assertTrue(triageHandler.isResolvable(),
            "TriageHandler should be resolvable — ReviewTriageHandler must be the sole winner");
    }

    @Test
    void triageHandlerIsReviewTriageHandler() {
        assertInstanceOf(ReviewTriageHandler.class, triageHandler.get(),
            "ReviewTriageHandler should displace NoOpTriageHandler and LlmTriageHandler");
    }
}
