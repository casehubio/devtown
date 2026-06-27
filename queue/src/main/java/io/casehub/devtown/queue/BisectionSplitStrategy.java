package io.casehub.devtown.queue;

import java.util.List;
import java.util.Map;

public interface BisectionSplitStrategy {

    SplitResult split(List<Map<String, Object>> prs, String batchId,
                      String targetBranch, int bisectionDepth,
                      String bisectionStrategy, String riskLevel);
}
