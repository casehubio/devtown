package io.casehub.devtown.queue;

import java.util.List;

public interface BatchCompositionPolicy {

    List<Batch> formBatches(List<QueuedPr> queue, BatchFormationContext ctx);
}
