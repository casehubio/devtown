package io.casehub.devtown.app;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class BatchCiRunnerWorkerTest {

    @Inject MergeBatchCaseHub caseHub;

    @BeforeEach
    void setUp() {
        caseHub.getDefinition();
    }

    @Test
    void workerIsRegistered() {
        var worker = caseHub.getDefinition().getWorkers().stream()
            .filter(w -> "batch-ci-runner".equals(w.name()))
            .findFirst();
        assertThat(worker).isPresent();
    }

    @Test
    void noOpClient_returnsFailed() {
        var result = caseHub.adaptBatchCiRunner(buildInput("casehubio/devtown"));
        assertThat(result.outcome().toString()).contains("Failed");
    }

    @Test
    void missingRepository_returnsFailed() {
        var input = buildInput(null);
        var result = caseHub.adaptBatchCiRunner(input);
        assertThat(result.outcome().toString()).contains("Failed");
    }

    @Test
    void invalidRepositoryFormat_returnsFailed() {
        var input = buildInput("no-slash");
        var result = caseHub.adaptBatchCiRunner(input);
        assertThat(result.outcome().toString()).contains("Failed");
    }

    private Map<String, Object> buildInput(String repository) {
        var batch = new LinkedHashMap<String, Object>();
        batch.put("id", "test-batch-1");
        if (repository != null) {
            batch.put("repository", repository);
        }
        batch.put("targetBranch", "main");
        batch.put("prs", List.of(
            Map.of("number", 1, "headSha", "sha-1"),
            Map.of("number", 2, "headSha", "sha-2")
        ));
        return Map.of("batch", batch);
    }
}
