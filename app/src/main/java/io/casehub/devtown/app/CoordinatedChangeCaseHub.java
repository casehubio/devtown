package io.casehub.devtown.app;

import io.casehub.api.engine.YamlCaseHub;
import io.casehub.api.model.CaseDefinition;
import io.casehub.devtown.domain.MergeClient;
import io.casehub.devtown.domain.MergeOutcome;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class CoordinatedChangeCaseHub extends YamlCaseHub {

    @Inject
    MergeClient mergeClient;

    public CoordinatedChangeCaseHub() {
        super("casehub/devtown/coordinated-change.yaml");
    }

    @Override
    protected void augment(CaseDefinition definition) {
        definition.getWorkers().add(Worker.builder()
            .name("coordinated-merge")
            .capabilityName("coordinated-merge")
            .function(this::adaptCoordinatedMerge)
            .build());
    }

    @SuppressWarnings("unchecked")
    WorkerResult adaptCoordinatedMerge(Map<String, Object> input) {
        List<Map<String, Object>> repos = (List<Map<String, Object>>) input.get("repos");
        List<Map<String, Object>> mergeResults = new ArrayList<>();

        for (Map<String, Object> repo : repos) {
            String owner = (String) repo.get("owner");
            String repoName = (String) repo.get("repo");
            int prNumber = ((Number) repo.get("prNumber")).intValue();
            String headSha = (String) repo.get("headSha");

            var result = new LinkedHashMap<String, Object>();
            result.put("repo", owner + "/" + repoName);

            switch (mergeClient.merge(owner, repoName, prNumber, headSha)) {
                case MergeOutcome.Success s -> {
                    result.put("status", "success");
                    result.put("mergeSha", s.mergeSha());
                }
                case MergeOutcome.Failure f -> {
                    result.put("status", "failed");
                    result.put("reason", f.reason());
                    mergeResults.add(result);
                    return WorkerResult.of(Map.of("mergeResults", mergeResults));
                }
            }
            mergeResults.add(result);
        }
        return WorkerResult.of(Map.of("mergeResults", mergeResults));
    }
}
