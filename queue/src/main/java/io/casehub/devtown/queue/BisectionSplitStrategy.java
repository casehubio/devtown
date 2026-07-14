package io.casehub.devtown.queue;

import java.util.List;

public interface BisectionSplitStrategy {

    SplitResult split(List<QueuedPr> prs, String batchId,
                      String targetBranch, int bisectionDepth,
                      String bisectionStrategy, String riskLevel);
}
