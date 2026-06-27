package io.casehub.devtown.app;

import io.casehub.devtown.queue.BatchCompositionPolicy;
import io.casehub.devtown.queue.BisectionSplitStrategy;
import io.casehub.devtown.queue.DefaultBatchCompositionPolicy;
import io.casehub.devtown.queue.TrustWeightedSplitStrategy;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class MergeQueueProducers {

    @Produces
    @DefaultBean
    @ApplicationScoped
    BisectionSplitStrategy bisectionSplitStrategy() {
        return new TrustWeightedSplitStrategy();
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    BatchCompositionPolicy batchCompositionPolicy() {
        return new DefaultBatchCompositionPolicy();
    }
}
