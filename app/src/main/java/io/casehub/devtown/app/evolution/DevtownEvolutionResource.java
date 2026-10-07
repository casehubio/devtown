package io.casehub.devtown.app.evolution;

import io.casehub.api.model.stigmergy.ConductorInboxEntry;
import io.casehub.api.model.stigmergy.GatePolicy;
import io.casehub.api.model.stigmergy.WatchPattern;
import io.casehub.api.view.DenyPatternView;
import io.casehub.api.view.EvolutionStateSnapshot.ImprovementStreamView;
import io.casehub.platform.api.mcp.HandWrittenEndpoint;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import java.util.List;

@Path("/api/devtown/evolution")
@HandWrittenEndpoint("Evolution conductor facade — delegates to EngineEvolutionApi with case resolution and view enrichment")
public class DevtownEvolutionResource {

  @Inject DevtownEvolutionApi api;

  @GET
  @Path("/state")
  public DevtownEvolutionStateSnapshot getState(@QueryParam("tenancyId") String tenancyId) {
    return api.getEvolutionState(tenancyId);
  }

  @GET
  @Path("/inbox")
  public List<ConductorInboxEntry> getInbox(@QueryParam("tenancyId") String tenancyId) {
    return api.getInbox(tenancyId);
  }

  @POST
  @Path("/inbox/{entryId}/resolve")
  public void resolveGate(
      @PathParam("entryId") String entryId,
      @QueryParam("tenancyId") String tenancyId,
      GateResolutionRequest request) {
    api.resolveGate(tenancyId, entryId, request.outcome(), request.reason(), request.feedback());
  }

  @GET
  @Path("/deny-patterns")
  public DenyPatternView getDenyPatterns(@QueryParam("tenancyId") String tenancyId) {
    return api.getDenyPatterns(tenancyId);
  }

  @POST
  @Path("/deny-patterns")
  public void addDenyPattern(@QueryParam("tenancyId") String tenancyId, String pattern) {
    api.addDenyPattern(tenancyId, pattern);
  }

  @DELETE
  @Path("/deny-patterns/{pattern}")
  public void removeDenyPattern(
      @PathParam("pattern") String pattern, @QueryParam("tenancyId") String tenancyId) {
    api.removeDenyPattern(tenancyId, pattern);
  }

  @GET
  @Path("/watch-patterns")
  public List<WatchPattern> getWatchPatterns(@QueryParam("tenancyId") String tenancyId) {
    return api.getWatchPatterns(tenancyId);
  }

  @DELETE
  @Path("/watch-patterns/{patternId}")
  public void removeWatchPattern(
      @PathParam("patternId") String patternId, @QueryParam("tenancyId") String tenancyId) {
    api.removeWatchPattern(tenancyId, patternId);
  }

  @GET
  @Path("/gate-policy")
  public GatePolicy getGatePolicy(@QueryParam("tenancyId") String tenancyId) {
    return api.getGatePolicy(tenancyId);
  }

  @PUT
  @Path("/gate-policy")
  public void setGatePolicy(@QueryParam("tenancyId") String tenancyId, GatePolicy policy) {
    api.setGatePolicy(tenancyId, policy);
  }

  @GET
  @Path("/streams")
  public List<ImprovementStreamView> getStreams(@QueryParam("tenancyId") String tenancyId) {
    return api.getStreams(tenancyId);
  }

  @POST
  @Path("/category/{category}/pause")
  public void pauseCategory(
      @PathParam("category") String category,
      @QueryParam("tenancyId") String tenancyId,
      @QueryParam("durationMinutes") int durationMinutes) {
    api.pauseCategory(tenancyId, category, durationMinutes);
  }

  @POST
  @Path("/category/{category}/unpause")
  public void unpauseCategory(
      @PathParam("category") String category, @QueryParam("tenancyId") String tenancyId) {
    api.unpauseCategory(tenancyId, category);
  }

  @POST
  @Path("/circuit-breaker/reset")
  public void resetCircuitBreaker(@QueryParam("tenancyId") String tenancyId) {
    api.resetCircuitBreaker(tenancyId);
  }
}
