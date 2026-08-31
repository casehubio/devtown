package io.casehub.devtown.app;

import io.casehub.api.model.JudgmentTarget;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class MergeQueueEntryCaseHubTest {

    @Inject MergeQueueEntryCaseHub caseHub;

    @Test
    void definitionLoads() {
        var def = caseHub.getDefinition();
        assertThat(def).isNotNull();
        assertThat(def.getNamespace()).isEqualTo("devtown");
        assertThat(def.getName()).isEqualTo("merge-queue-entry");
        assertThat(def.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void hasFourGoals() {
        var def = caseHub.getDefinition();
        assertThat(def.getGoals()).hasSize(4);
        var names = def.getGoals().stream().map(g -> g.getName()).toList();
        assertThat(names).containsExactlyInAnyOrder(
            "pr-merged", "pr-rejected", "pr-withdrawn", "pr-conflict"
        );
    }

    @Test
    void hasOneBinding() {
        var def = caseHub.getDefinition();
        assertThat(def.getBindings()).hasSize(1);
        assertThat(def.getBindings().get(0).getName()).isEqualTo("sla-breach-escalation");
    }

    @Test
    void hasCompletion() {
        var def = caseHub.getDefinition();
        assertThat(def.getCompletion()).isNotNull();
    }

    @Test
    void slaBreachEscalationHasHumanTask() {
        var def = caseHub.getDefinition();
        var binding = def.getBindings().stream()
            .filter(b -> "sla-breach-escalation".equals(b.getName()))
            .findFirst().orElseThrow();
        assertThat(binding.target()).isInstanceOf(JudgmentTarget.class);
        var ht = (JudgmentTarget) binding.target();
        assertThat(ht.outcomes()).containsExactlyInAnyOrder("PRIORITIZE", "DEQUEUE", "ACKNOWLEDGE");
    }
}
