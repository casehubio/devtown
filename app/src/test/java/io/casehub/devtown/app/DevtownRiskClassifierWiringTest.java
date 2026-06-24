package io.casehub.devtown.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.spi.ActionRiskClassifier;
import io.casehub.api.spi.ClassificationContext;
import io.casehub.api.spi.RiskClassifier;
import io.casehub.api.spi.RiskDecision;
import io.casehub.worker.api.PlannedAction;
import io.casehub.devtown.domain.DevtownActionType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DevtownRiskClassifierWiringTest {

    @Inject
    @RiskClassifier
    ActionRiskClassifier classifier;

    @Test
    void classifierBeanDiscovered() {
        assertThat(classifier).isNotNull();
        assertThat(classifier).isInstanceOf(DevtownRiskClassifierProducer.class);
    }

    @Test
    void classifyReturnsGateRequiredForForceMerge() {
        var action = PlannedAction.of("Force merge PR", DevtownActionType.PR_FORCE_MERGE, Map.of());
        var context = new ClassificationContext("test-worker", UUID.randomUUID(), "test-tenant",
                "pr-review", "merge-executor", "merge-binding");
        RiskDecision result = classifier.classify(action, context);
        assertThat(result).isInstanceOf(RiskDecision.GateRequired.class);
    }

    @Test
    void classifyReturnsAutonomousForApprovedMerge() {
        var action = PlannedAction.of("Merge PR", DevtownActionType.PR_MERGE_EXECUTE,
                Map.of("approvedReviewCount", 2));
        var context = new ClassificationContext("test-worker", UUID.randomUUID(), "test-tenant",
                "pr-review", "merge-executor", "merge-binding");
        RiskDecision result = classifier.classify(action, context);
        assertThat(result).isInstanceOf(RiskDecision.Autonomous.class);
    }
}
