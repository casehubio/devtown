package io.casehub.devtown.domain;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class MergeQueueCapabilityTest {

    @Test
    void constantsMatchYamlCapabilityNames() {
        assertThat(MergeQueueCapability.BATCH_CI_RUNNER).isEqualTo("batch-ci-runner");
        assertThat(MergeQueueCapability.BISECTION_SPLITTER).isEqualTo("bisection-splitter");
        assertThat(MergeQueueCapability.PR_REJECT_AND_NOTIFY).isEqualTo("pr-reject-and-notify");
        assertThat(MergeQueueCapability.MERGE_QUEUE_ENQUEUE).isEqualTo("merge-queue-enqueue");
    }

    @Test
    void allCapabilitiesSetIsComplete() {
        assertThat(MergeQueueCapability.ALL).containsExactlyInAnyOrder(
            "batch-ci-runner", "bisection-splitter",
            "pr-reject-and-notify", "merge-queue-enqueue");
    }
}
