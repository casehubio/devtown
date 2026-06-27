package io.casehub.devtown.queue;

import java.util.List;
import java.util.Map;

public record BatchSlice(
    String id,
    String targetBranch,
    List<Map<String, Object>> prs,
    int size,
    String parentBatchId,
    int bisectionDepth,
    String bisectionStrategy,
    String riskLevel
) {}
