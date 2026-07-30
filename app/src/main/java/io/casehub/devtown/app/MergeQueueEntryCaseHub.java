package io.casehub.devtown.app;

import io.casehub.api.engine.YamlCaseHub;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MergeQueueEntryCaseHub extends YamlCaseHub {

    public MergeQueueEntryCaseHub() {
        super("devtown/merge-queue-entry.yaml");
    }
}
