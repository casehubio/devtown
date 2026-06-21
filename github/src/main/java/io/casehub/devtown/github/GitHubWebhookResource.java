package io.casehub.devtown.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.devtown.review.LifecycleResult;
import io.casehub.devtown.review.PrReviewApplicationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Map;

@Path("/api/github/webhook")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GitHubWebhookResource {

    private static final Logger LOG = Logger.getLogger(GitHubWebhookResource.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    PrReviewApplicationService service;

    @ConfigProperty(name = "devtown.github.webhook-secret", defaultValue = "")
    String webhookSecret;

    @POST
    public Response receive(String body,
                            @HeaderParam("X-GitHub-Event") String eventType,
                            @HeaderParam("X-Hub-Signature-256") String signature,
                            @HeaderParam("X-GitHub-Delivery") String deliveryId) {

        if (!GitHubSignatureVerifier.verify(body, signature, webhookSecret)) {
            return Response.status(401).entity(Map.of("error", "invalid signature")).build();
        }

        if (eventType == null) {
            return ok(Map.of("status", "ignored", "reason", "missing-event-type"));
        }

        try {
            return switch (eventType) {
                case "pull_request" -> handlePullRequest(body);
                default -> ok(Map.of("status", "ignored", "event", eventType));
            };
        } catch (Exception e) {
            LOG.errorf(e, "Webhook processing failed. deliveryId=%s body=%s", deliveryId, body);
            return Response.serverError().entity(Map.of("error", "internal")).build();
        }
    }

    private Response handlePullRequest(String body) throws Exception {
        var event = MAPPER.readValue(body, GitHubPullRequestEvent.class);

        return switch (event.action()) {
            case "opened" -> handleOpened(event);
            case "ready_for_review" -> handleReadyForReview(event);
            case "synchronize" -> handleSynchronize(event);
            case "closed" -> handleClosed(event);
            case "reopened" -> handleReopened(event);
            default -> ok(Map.of("status", "ignored", "action", event.action()));
        };
    }

    private Response handleOpened(GitHubPullRequestEvent event) {
        if (event.pull_request().draft()) {
            return ok(Map.of("status", "ignored", "reason", "draft"));
        }
        service.startReview(GitHubPayloadMapper.toPrPayload(event));
        return ok(Map.of("status", "accepted", "action", "case-started"));
    }

    private Response handleReadyForReview(GitHubPullRequestEvent event) {
        service.startReview(GitHubPayloadMapper.toPrPayload(event));
        return ok(Map.of("status", "accepted", "action", "case-started"));
    }

    private Response handleSynchronize(GitHubPullRequestEvent event) {
        var pr = event.pull_request();
        var result = service.revisePr(
            event.repository().full_name(), event.number(),
            pr.head().sha(), pr.additions() + pr.deletions()
        );
        return ok(Map.of("status", "accepted", "action", lifecycleAction("case-updated", result)));
    }

    private Response handleClosed(GitHubPullRequestEvent event) {
        boolean merged = event.pull_request().merged();
        var result = service.closePr(event.repository().full_name(), event.number(), merged);
        String action = merged ? "case-merged" : "case-abandoned";
        return ok(Map.of("status", "accepted", "action", lifecycleAction(action, result)));
    }

    private Response handleReopened(GitHubPullRequestEvent event) {
        service.startReview(GitHubPayloadMapper.toPrPayload(event));
        return ok(Map.of("status", "accepted", "action", "case-started"));
    }

    private static String lifecycleAction(String normalAction, LifecycleResult result) {
        return switch (result) {
            case UPDATED -> normalAction;
            case NO_ACTIVE_CASE -> "no-active-case";
            case ALREADY_COMPLETED -> "already-completed";
            case ALREADY_ABANDONED -> "already-abandoned";
        };
    }

    private static Response ok(Map<String, Object> body) {
        return Response.ok(body).build();
    }
}
