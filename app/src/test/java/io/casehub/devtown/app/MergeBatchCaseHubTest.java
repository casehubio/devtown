package io.casehub.devtown.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.Binding;
import io.casehub.api.model.HumanTaskTarget;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class MergeBatchCaseHubTest {

    @Inject MergeBatchCaseHub caseHub;

    @Test
    void definitionLoads() {
        var def = caseHub.getDefinition();
        assertThat(def).isNotNull();
        assertThat(def.getNamespace()).isEqualTo("devtown");
        assertThat(def.getName()).isEqualTo("merge-batch");
        assertThat(def.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void hasElevenBindings() {
        var def = caseHub.getDefinition();
        assertThat(def.getBindings()).hasSize(11);
        var names = def.getBindings().stream().map(Binding::getName).toList();
        assertThat(names).containsExactlyInAnyOrder(
            "test-batch-tip",
            "tip-test-escalation",
            "tip-test-after-escalation",
            "merge-batch",
            "human-merge-approval",
            "merge-escalation",
            "merge-after-escalation",
            "compute-bisection-split",
            "bisect-left",
            "bisect-right",
            "reject-single-pr"
        );
    }

    @Test
    void hasSixGoals() {
        var def = caseHub.getDefinition();
        assertThat(def.getGoals()).hasSize(6);
        var names = def.getGoals().stream().map(g -> g.getName()).toList();
        assertThat(names).containsExactlyInAnyOrder(
            "batch-merged",
            "all-culprits-isolated",
            "single-pr-rejected",
            "merge-approval-rejected",
            "merge-terminal-failure",
            "tip-test-terminal-failure"
        );
    }

    @Test
    void hasFourCapabilities() {
        var def = caseHub.getDefinition();
        assertThat(def.getCapabilities()).hasSize(4);
        var names = def.getCapabilities().stream().map(c -> c.name()).toList();
        assertThat(names).containsExactlyInAnyOrder(
            "batch-ci-runner",
            "merge-executor",
            "bisection-splitter",
            "pr-reject-and-notify"
        );
    }

    @Test
    void hasCompletion() {
        var def = caseHub.getDefinition();
        assertThat(def.getCompletion()).isNotNull();
    }

    @Test
    void escalationBindingsHaveHumanTask() {
        var def = caseHub.getDefinition();
        var escalationBindings = def.getBindings().stream()
            .filter(b -> b.getName().contains("escalation") || b.getName().equals("human-merge-approval"))
            .filter(b -> !b.getName().equals("tip-test-after-escalation") && !b.getName().equals("merge-after-escalation"))
            .toList();
        assertThat(escalationBindings).hasSize(3);
        for (Binding b : escalationBindings) {
            assertThat(b.target())
                .as("binding '%s' should target a humanTask", b.getName())
                .isInstanceOf(HumanTaskTarget.class);
        }
    }

    @Test
    void bisectionBindingsAreSubCases() {
        var def = caseHub.getDefinition();
        var bisectionBindings = def.getBindings().stream()
            .filter(b -> b.getName().startsWith("bisect-"))
            .toList();
        assertThat(bisectionBindings).hasSize(2);
        for (Binding b : bisectionBindings) {
            assertThat(b.target())
                .as("binding '%s' should target a subCase", b.getName())
                .isInstanceOf(io.casehub.api.model.SubCaseTarget.class);
        }
    }

    @Test
    void capabilityBindingsWithRerouteHaveOutcomePolicy() {
        var def = caseHub.getDefinition();
        // reject-single-pr is a terminal action — no rerouting needed
        var capabilityBindings = def.getBindings().stream()
            .filter(b -> b.target() instanceof io.casehub.api.model.CapabilityTarget)
            .filter(b -> !"reject-single-pr".equals(b.getName()))
            .toList();
        assertThat(capabilityBindings).isNotEmpty();
        for (Binding b : capabilityBindings) {
            assertThat(b.getOutcomePolicy())
                .as("binding '%s' should have outcomePolicy", b.getName())
                .isNotNull();
        }
    }
}
