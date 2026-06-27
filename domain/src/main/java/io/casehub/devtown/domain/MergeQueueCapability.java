package io.casehub.devtown.domain;

import java.util.Set;

public final class MergeQueueCapability {

    public static final String BATCH_CI_RUNNER       = "batch-ci-runner";
    public static final String BISECTION_SPLITTER    = "bisection-splitter";
    public static final String PR_REJECT_AND_NOTIFY  = "pr-reject-and-notify";
    public static final String MERGE_QUEUE_ENQUEUE   = "merge-queue-enqueue";

    public static final Set<String> ALL = Set.of(
        BATCH_CI_RUNNER, BISECTION_SPLITTER,
        PR_REJECT_AND_NOTIFY, MERGE_QUEUE_ENQUEUE
    );

    private MergeQueueCapability() {}
}
