package io.casehub.devtown.app;

import io.casehub.devtown.merge.MergeQueuePort;
import io.casehub.devtown.review.PrReviewApplicationService;
import io.casehub.devtown.review.PrReviewSubmission;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Path("/api")
@jakarta.annotation.security.PermitAll
@io.casehub.platform.api.mcp.HandWrittenEndpoint("stub + action endpoints for dev-mode UI")
public class DevModeStubResource {

    @Inject PrReviewApplicationService reviewService;
    @Inject MergeQueuePort mergeQueue;
    @Inject io.casehub.devtown.app.mcp.PrReviewCaseTracker caseTracker;
    @Inject PrReviewCaseHub caseHub;
    @Inject
            io.casehub.work.api.spi.WorkItemOperations workItemOperations;


    @GET
    @Path("/sessions")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Object> sessions() {
        return List.of();
    }

    @GET
    @Path("/v1/case-definitions")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Map<String, Object>> caseDefinitions() {
        return List.of();
    }

    @POST
    @Path("/actions/approve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> approve(Map<String, Object> body) {
        String repo = (String) body.get("repo");
        int prNumber = ((Number) body.get("prNumber")).intValue();
        String contributor = (String) body.getOrDefault("contributor", "unknown");
        var result = reviewService.signalReviewSubmitted(new PrReviewSubmission(
                repo, prNumber, "approved", System.currentTimeMillis(), contributor, -1));
        return Map.of("action", "approved", "result", result.name(), "repo", repo, "prNumber", prNumber);
    }

    @POST
    @Path("/actions/request-changes")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> requestChanges(Map<String, Object> body) {
        String repo = (String) body.get("repo");
        int prNumber = ((Number) body.get("prNumber")).intValue();
        String contributor = (String) body.getOrDefault("contributor", "unknown");
        var result = reviewService.signalReviewSubmitted(new PrReviewSubmission(
                repo, prNumber, "changes_requested", System.currentTimeMillis(), contributor, -1));
        return Map.of("action", "changes_requested", "result", result.name(), "repo", repo, "prNumber", prNumber);
    }

    @POST
    @Path("/actions/enqueue")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> enqueue(Map<String, Object> body) {
        String repo     = (String) body.get("repo");
        int    prNumber = ((Number) body.get("prNumber")).intValue();
        String headSha  = (String) body.getOrDefault("headSha", "unknown");
        String author   = (String) body.getOrDefault("contributor", "unknown");
        try {
            var result = mergeQueue.admit(prNumber, repo, headSha, author);
            return Map.of("action", "enqueued", "result", result.name(), "repo", repo, "prNumber", prNumber);
        } catch (Exception e) {
            String    msg  = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            Throwable root = e;
            while (root.getCause() != null) {root = root.getCause();}
            if (root != e) {
                msg = msg + " — root: " + (root.getMessage() != null ? root.getMessage() : root.getClass().getName());
            }
            return Map.of("action", "enqueue_failed", "error", msg, "repo", repo, "prNumber", prNumber);
        }
    }

    @POST
    @Path("/actions/dequeue")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> dequeue(Map<String, Object> body) {
        String repo = (String) body.get("repo");
        int prNumber = ((Number) body.get("prNumber")).intValue();
        boolean removed = mergeQueue.dequeue(prNumber, repo);
        return Map.of("action", "dequeued", "removed", removed, "repo", repo, "prNumber", prNumber);
    }

    @POST
    @Path("/actions/signal-ci-pass")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> signalCiPass(Map<String, Object> body) {
        String repo = (String) body.get("repo");
        int prNumber = ((Number) body.get("prNumber")).intValue();
        var activeCase = caseTracker.findActiveCaseByPr(repo, prNumber);
        if (activeCase.isEmpty()) {
            return Map.of("action", "signal-ci-pass", "result", "no_active_case", "repo", repo, "prNumber", prNumber);
        }
        caseHub.signal(activeCase.get().caseId(), "ci.status", "passing");
        return Map.of("action", "signal-ci-pass", "result", "signalled", "repo", repo, "prNumber", prNumber,
            "caseId", activeCase.get().caseId().toString());
    }

    @POST
    @Path("/actions/claim-workitem")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> claimWorkItem(Map<String, Object> body) {
        String workItemId = (String) body.get("workItemId");
        String claimant   = (String) body.getOrDefault("claimant", "dev-user");
        var    wi         = workItemOperations.claim(java.util.UUID.fromString(workItemId), claimant);
        return Map.of("action", "claim-workitem", "result", wi.status().name(),
                      "workItemId", workItemId, "assignee", claimant);
    }

    @POST
    @Path("/actions/decide-workitem")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> decideWorkItem(Map<String, Object> body) {
        String workItemId = (String) body.get("workItemId");
        String outcome    = (String) body.getOrDefault("outcome", "APPROVED");
        String actor      = (String) body.getOrDefault("actor", "dev-user");
        var    id         = java.util.UUID.fromString(workItemId);
        var wi = switch (outcome.toUpperCase()) {
            case "APPROVED" -> workItemOperations.complete(id, actor, null, "APPROVED");
            case "REJECTED" -> workItemOperations.reject(id, actor, "Rejected", "REJECTED");
            default -> throw new IllegalArgumentException("Unknown outcome: " + outcome);
        };
        return Map.of("action", "decide-workitem", "result", wi.status().name(),
                      "workItemId", workItemId, "outcome", outcome);
    }

}
