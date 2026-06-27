package io.casehub.devtown.queue;

import java.util.List;

public record Batch(
    String id,
    List<QueuedPr> prs,
    String targetBranch,
    String riskLevel,
    String bisectionStrategy
) {
    public int size() { return prs.size(); }
}
