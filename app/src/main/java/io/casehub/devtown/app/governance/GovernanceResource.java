package io.casehub.devtown.app.governance;

import io.casehub.devtown.app.mcp.TrackedEvent;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Path("/api/governance")
@Produces(MediaType.APPLICATION_JSON)
@jakarta.annotation.security.PermitAll
public class GovernanceResource {

    @Inject
    GovernanceQueryService queryService;
    @Inject
    TrustQueryService      trustQueryService;


    @GET
    @Path("/queue-status")
    public GovernanceQueryService.QueueStatus queueStatus() {
        return queryService.queueStatus();
    }

    @GET
    @Path("/recent-events")
    public List<TrackedEvent> recentEvents(
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("since") String since) {
        Instant sinceTime = since != null ? Instant.parse(since) : null;
        return queryService.recentEvents(limit, sinceTime);
    }

    @GET
    @Path("/system-health")
    public GovernanceQueryService.SystemHealth systemHealth() {
        return queryService.systemHealth();
    }

    @GET
    @Path("/problems")
    public PagedResult<GovernanceQueryService.Problem> problems(
            @QueryParam("threshold_minutes") @DefaultValue("60") int thresholdMinutes,
            @QueryParam("cursor") String cursor,
            @QueryParam("limit") @DefaultValue("50") int limit) {
        return PagedResult.paginate(queryService.problems(thresholdMinutes), cursor, limit);
    }

    @GET
    @Path("/reviews")
    public PagedResult<GovernanceQueryService.ReviewListEntry> reviewsList(
            @QueryParam("cursor") String cursor,
            @QueryParam("limit") @DefaultValue("50") int limit) {
        return PagedResult.paginate(queryService.reviewsList(), cursor, limit);
    }

    @GET
    @Path("/reviews/{caseId}")
    public GovernanceQueryService.ReviewDetail reviewDetail(@PathParam("caseId") UUID caseId) {
        return queryService.reviewDetail(caseId);
    }

    @GET
    @Path("/reviewers")
    public PagedResult<GovernanceQueryService.ReviewerFleetEntry> reviewerFleet(
            @QueryParam("cursor") String cursor,
            @QueryParam("limit") @DefaultValue("50") int limit) {
        return PagedResult.paginate(queryService.reviewerFleet(), cursor, limit);
    }

    @GET
    @Path("/reviewers/{actorId}")
    public GovernanceQueryService.ReviewerHealth reviewerHealth(@PathParam("actorId") String actorId) {
        return queryService.reviewerHealth(actorId);
    }

    @GET
    @Path("/merge-queue")
    public GovernanceQueryService.MergeQueueStatus mergeQueue() {
        return queryService.mergeQueue();
    }

    @GET
    @Path("/merge-queue/metrics")
    public GovernanceQueryService.MergeQueueMetrics mergeQueueMetrics() {
        return queryService.mergeQueueMetrics();
    }

    @GET
    @Path("/merge-queue/batch/{batchId}")
    public GovernanceQueryService.BatchStatus batchStatus(@PathParam("batchId") UUID batchId) {
        return queryService.batchStatus(batchId);
    }

    @GET
    @Path("/triage")
    public PagedResult<GovernanceQueryService.TriageItem> triageItems(
            @QueryParam("cursor") String cursor,
            @QueryParam("limit") @DefaultValue("50") int limit) {
        return PagedResult.paginate(queryService.triageItems(), cursor, limit);
    }

    @GET
    @Path("/sla-comparison")
    public GovernanceQueryService.SlaComparison slaComparison() {
        return queryService.slaComparison();
    }

    @GET
    @Path("/trust/{actorId}")
    public TrustQueryService.TrustScoreResponse trustScore(@PathParam("actorId") String actorId) {
        return trustQueryService.trustScore(actorId);
    }

    @GET
    @Path("/trust/{actorId}/trend")
    public List<TrustQueryService.TrustTrendPoint> trustTrend(
            @PathParam("actorId") String actorId,
            @QueryParam("capability") String capability,
            @QueryParam("limit") @DefaultValue("30") int limit) {
        return trustQueryService.trustTrend(actorId, capability, limit);
    }

    @GET
    @Path("/trust/{actorId}/routing-history")
    public List<TrustQueryService.RoutingDecisionSummary> routingHistory(
            @PathParam("actorId") String actorId,
            @QueryParam("capability") String capability,
            @QueryParam("limit") @DefaultValue("50") int limit) {
        return trustQueryService.routingHistory(actorId, capability, limit);
    }

    @GET
    @Path("/trust/{actorId}/routing-history/{entryId}")
    public Response routingDetail(
            @PathParam("actorId") String actorId,
            @PathParam("entryId") UUID entryId) {
        var detail = trustQueryService.routingDetail(actorId, entryId);
        if (detail == null) {
            return Response.status(404).build();
        }
        return Response.ok(detail).build();
    }
}
