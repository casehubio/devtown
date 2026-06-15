package io.casehub.devtown.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.spi.ActionRiskClassifier;
import io.casehub.api.spi.PlannedAction;
import io.casehub.api.spi.RiskClassifier;
import io.casehub.api.spi.RiskDecision;
import io.casehub.devtown.domain.DevtownActionType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Map;
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
        RiskDecision result = classifier.classify(action);
        assertThat(result).isInstanceOf(RiskDecision.GateRequired.class);
    }

    @Test
    void classifyReturnsAutonomousForApprovedMerge() {
        var action = PlannedAction.of("Merge PR", DevtownActionType.PR_MERGE_EXECUTE,
                Map.of("approvedReviewCount", 2));
        RiskDecision result = classifier.classify(action);
        assertThat(result).isInstanceOf(RiskDecision.Autonomous.class);
    }
}
