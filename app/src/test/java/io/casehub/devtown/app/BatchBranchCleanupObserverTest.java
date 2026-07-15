package io.casehub.devtown.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.devtown.domain.BatchBranchClient;
import io.casehub.devtown.domain.BranchDeleteOutcome;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

class BatchBranchCleanupObserverTest {

    private BatchBranchClient client;
    private BatchBranchCleanupObserver observer;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        client = mock(BatchBranchClient.class);
        observer = new BatchBranchCleanupObserver();
        observer.batchBranchClient = client;
    }

    @Test
    void terminalMergeBatch_callsDeleteBranch() {
        when(client.deleteBatchBranch("casehubio", "devtown", "merge-queue/batch-b1"))
            .thenReturn(new BranchDeleteOutcome.Deleted("merge-queue/batch-b1"));

        observer.onCaseLifecycle(event("COMPLETED", "devtown", "merge-batch",
            Map.of("batch", Map.of("id", "b1", "repository", "casehubio/devtown"))));

        verify(client).deleteBatchBranch("casehubio", "devtown", "merge-queue/batch-b1");
    }

    @Test
    void faultedCase_triggersCleanup() {
        when(client.deleteBatchBranch(any(), any(), any()))
            .thenReturn(new BranchDeleteOutcome.Deleted("merge-queue/batch-b1"));

        observer.onCaseLifecycle(event("FAULTED", "devtown", "merge-batch",
            Map.of("batch", Map.of("id", "b1", "repository", "casehubio/devtown"))));

        verify(client).deleteBatchBranch("casehubio", "devtown", "merge-queue/batch-b1");
    }

    @Test
    void cancelledCase_triggersCleanup() {
        when(client.deleteBatchBranch(any(), any(), any()))
            .thenReturn(new BranchDeleteOutcome.Deleted("merge-queue/batch-b1"));

        observer.onCaseLifecycle(event("CANCELLED", "devtown", "merge-batch",
            Map.of("batch", Map.of("id", "b1", "repository", "casehubio/devtown"))));

        verify(client).deleteBatchBranch("casehubio", "devtown", "merge-queue/batch-b1");
    }

    @Test
    void nonTerminalStatus_noCleanup() {
        observer.onCaseLifecycle(event("RUNNING", "devtown", "merge-batch",
            Map.of("batch", Map.of("id", "b1", "repository", "casehubio/devtown"))));

        verifyNoInteractions(client);
    }

    @Test
    void nonMergeBatchDefinition_noCleanup() {
        observer.onCaseLifecycle(event("COMPLETED", "devtown", "pr-review",
            Map.of("batch", Map.of("id", "b1", "repository", "casehubio/devtown"))));

        verifyNoInteractions(client);
    }

    @Test
    void nonDevtownNamespace_noCleanup() {
        observer.onCaseLifecycle(event("COMPLETED", "other-ns", "merge-batch",
            Map.of("batch", Map.of("id", "b1", "repository", "casehubio/devtown"))));

        verifyNoInteractions(client);
    }

    @Test
    void nullCaseStatus_noCleanup() {
        observer.onCaseLifecycle(event(null, "devtown", "merge-batch",
            Map.of("batch", Map.of("id", "b1", "repository", "casehubio/devtown"))));

        verifyNoInteractions(client);
    }

    @Test
    void nullContextSnapshot_noCleanup() {
        var event = new CaseLifecycleEvent(
            UUID.randomUUID(), "t1", "cmd", "evt", "COMPLETED",
            null, null, null, "merge-batch", "devtown", null);
        observer.onCaseLifecycle(event);

        verifyNoInteractions(client);
    }

    @Test
    void missingBatchId_noCleanup() {
        observer.onCaseLifecycle(event("COMPLETED", "devtown", "merge-batch",
            Map.of("batch", Map.of("repository", "casehubio/devtown"))));

        verifyNoInteractions(client);
    }

    @Test
    void invalidRepositoryFormat_noCleanup() {
        observer.onCaseLifecycle(event("COMPLETED", "devtown", "merge-batch",
            Map.of("batch", Map.of("id", "b1", "repository", "no-slash"))));

        verifyNoInteractions(client);
    }

    @Test
    void deleteNotFound_noException() {
        when(client.deleteBatchBranch(any(), any(), any()))
            .thenReturn(new BranchDeleteOutcome.NotFound("merge-queue/batch-b1"));

        observer.onCaseLifecycle(event("COMPLETED", "devtown", "merge-batch",
            Map.of("batch", Map.of("id", "b1", "repository", "casehubio/devtown"))));

        verify(client).deleteBatchBranch("casehubio", "devtown", "merge-queue/batch-b1");
    }

    @Test
    void deleteFailure_noException() {
        when(client.deleteBatchBranch(any(), any(), any()))
            .thenReturn(new BranchDeleteOutcome.Failure("api error"));

        observer.onCaseLifecycle(event("COMPLETED", "devtown", "merge-batch",
            Map.of("batch", Map.of("id", "b1", "repository", "casehubio/devtown"))));

        verify(client).deleteBatchBranch("casehubio", "devtown", "merge-queue/batch-b1");
    }

    private CaseLifecycleEvent event(String status, String namespace,
                                      String defName, Map<String, Object> context) {
        JsonNode contextNode = mapper.valueToTree(context);
        return new CaseLifecycleEvent(
            UUID.randomUUID(), "t1", "cmd", "evt", status,
            null, null, null, defName, namespace, contextNode);
    }
}
