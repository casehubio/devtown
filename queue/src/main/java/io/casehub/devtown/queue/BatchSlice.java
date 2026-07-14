package io.casehub.devtown.queue;

import java.util.List;

public record BatchSlice(
        String id,
        String targetBranch,
        List<QueuedPr> prs,
        int size,
        String parentBatchId,
        int bisectionDepth,
        String bisectionStrategy,
        String riskLevel
) {}
