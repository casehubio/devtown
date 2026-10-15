package io.casehub.devtown.app;

import io.casehub.devtown.review.PrPayload;
import io.casehub.devtown.review.PrReviewApplicationService;
import io.casehub.devtown.review.PrReviewSubmission;
import io.casehub.platform.api.mcp.HandWrittenEndpoint;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@Path("/api/dev/webhook-replay")
@jakarta.annotation.security.PermitAll
@HandWrittenEndpoint("Dev-mode webhook replay with identity override for multi-user simulation")
public class WebhookReplayResource {

    @Inject PrReviewApplicationService reviewService;
    @Inject PrReviewCaseHub caseHub;
    @Inject io.casehub.devtown.app.mcp.PrReviewCaseTracker caseTracker;

    @POST
    @Path("/pr-opened")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> replayPrOpened(Map<String, Object> body) {
        String user = (String) body.getOrDefault("user", "alice");
        int prNumber = ((Number) body.getOrDefault("prNumber", 101)).intValue();
        String repo = (String) body.getOrDefault("repo", "casehubio/webapp");
        String headSha = (String) body.getOrDefault("headSha", "abc" + prNumber);
        String baseRef = (String) body.getOrDefault("baseRef", "main");
        int linesChanged = ((Number) body.getOrDefault("linesChanged", 150)).intValue();
        @SuppressWarnings("unchecked")
        List<String> changedPaths = (List<String>) body.getOrDefault("changedPaths",
                List.of("src/main/java/com/example/Service.java"));

        var payload = new PrPayload(repo, prNumber, headSha, baseRef, linesChanged,
                user, userNumericId(user), changedPaths);
        reviewService.startReview(payload);
        return Map.of("status", "accepted", "preset", "pr-opened",
                "user", user, "prNumber", prNumber, "repo", repo);
    }

    @POST
    @Path("/review-approved")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> replayReviewApproved(Map<String, Object> body) {
        String user = (String) body.getOrDefault("user", "bob");
        int prNumber = ((Number) body.getOrDefault("prNumber", 101)).intValue();
        String repo = (String) body.getOrDefault("repo", "casehubio/webapp");

        var result = reviewService.signalReviewSubmitted(new PrReviewSubmission(
                repo, prNumber, "approved", System.currentTimeMillis(),
                user, userNumericId(user)));
        return Map.of("status", "accepted", "preset", "review-approved",
                "user", user, "prNumber", prNumber, "result", result.name());
    }

    @POST
    @Path("/review-changes-requested")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> replayReviewChangesRequested(Map<String, Object> body) {
        String user = (String) body.getOrDefault("user", "charlie");
        int prNumber = ((Number) body.getOrDefault("prNumber", 101)).intValue();
        String repo = (String) body.getOrDefault("repo", "casehubio/webapp");

        var result = reviewService.signalReviewSubmitted(new PrReviewSubmission(
                repo, prNumber, "changes_requested", System.currentTimeMillis(),
                user, userNumericId(user)));
        return Map.of("status", "accepted", "preset", "review-changes-requested",
                "user", user, "prNumber", prNumber, "result", result.name());
    }

    @POST
    @Path("/pr-synchronize")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> replayPrSynchronize(Map<String, Object> body) {
        String user = (String) body.getOrDefault("user", "alice");
        int prNumber = ((Number) body.getOrDefault("prNumber", 101)).intValue();
        String repo = (String) body.getOrDefault("repo", "casehubio/webapp");
        String headSha = (String) body.getOrDefault("headSha", "rev" + System.currentTimeMillis());
        int linesChanged = ((Number) body.getOrDefault("linesChanged", 200)).intValue();

        var result = reviewService.revisePr(repo, prNumber, headSha, linesChanged);
        return Map.of("status", "accepted", "preset", "pr-synchronize",
                "user", user, "prNumber", prNumber, "result", result.name());
    }

    @POST
    @Path("/ci-pass")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> replayCiPass(Map<String, Object> body) {
        int prNumber = ((Number) body.getOrDefault("prNumber", 101)).intValue();
        String repo = (String) body.getOrDefault("repo", "casehubio/webapp");

        var activeCase = caseTracker.findActiveCaseByPr(repo, prNumber);
        if (activeCase.isEmpty()) {
            return Map.of("status", "no_active_case", "preset", "ci-pass",
                    "repo", repo, "prNumber", prNumber);
        }
        caseHub.signal(activeCase.get().caseId(), "ci.status", "passing");
        return Map.of("status", "accepted", "preset", "ci-pass",
                "repo", repo, "prNumber", prNumber,
                "caseId", activeCase.get().caseId().toString());
    }

    private static long userNumericId(String login) {
        return switch (login) {
            case "alice" -> 1001;
            case "bob" -> 1002;
            case "charlie" -> 1003;
            case "dave" -> 1004;
            default -> Math.abs(login.hashCode()) + 2000L;
        };
    }
}
