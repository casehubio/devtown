package io.casehub.devtown.app;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class PrReviewCoordinatedModeTest {

    @Inject
    PrReviewCaseHub caseHub;

    @Test
    void mergeDirectBindingHasCoordinatedChangeGuard() {
        var def = caseHub.getDefinition();
        var mergeDirect = def.getBindings().stream()
                             .filter(b -> "merge-direct".equals(b.getName()))
                             .findFirst().orElseThrow();
        assertThat(jqExpression(mergeDirect.getWhen())).contains("coordinatedChange");
    }

    @Test
    void enqueueForMergeBindingHasCoordinatedChangeGuard() {
        var def = caseHub.getDefinition();
        var enqueue = def.getBindings().stream()
                         .filter(b -> "enqueue-for-merge".equals(b.getName()))
                         .findFirst().orElseThrow();
        assertThat(jqExpression(enqueue.getWhen())).contains("coordinatedChange");
    }

    @Test
    void mergeCompletedGoalIncludesCoordinatedChangeCondition() {
        var def = caseHub.getDefinition();
        var mergeCompleted = def.getGoals().stream()
                                .filter(g -> "merge-completed".equals(g.getName()))
                                .findFirst().orElseThrow();
        assertThat(jqExpression(mergeCompleted.getCondition())).contains("coordinatedChange");
    }

    private static String jqExpression(io.casehub.api.model.evaluator.ExpressionEvaluator evaluator) {
        return ((io.casehub.api.model.evaluator.JQExpressionEvaluator) evaluator).expression();
    }
}
