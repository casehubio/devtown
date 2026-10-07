package io.casehub.devtown.app.evolution;

import io.casehub.api.model.improvement.ImprovementConfig;
import io.casehub.api.model.stigmergy.ConductorInboxEntry;
import io.casehub.api.model.stigmergy.GatePolicy;
import io.casehub.api.model.stigmergy.WatchPattern;
import io.casehub.api.spi.improvement.EngineEvolutionApi;
import io.casehub.api.view.DenyPatternView;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DevtownEvolutionApi {

  private static final ImprovementConfig DEFAULT_CONFIG =
      new ImprovementConfig(null, null, null, null, null);

  @Inject EngineEvolutionApi engineApi;
  @Inject DevtownEvolutionCaseResolver caseResolver;
  @Inject DevtownEvolutionEnricher enricher;

  public DevtownEvolutionStateSnapshot getEvolutionState(String tenancyId) {
    UUID caseId = caseResolver.resolve(tenancyId);
    var state = engineApi.getEvolutionState(caseId, tenancyId, DEFAULT_CONFIG);
    return enricher.enrich(state);
  }

  public List<ConductorInboxEntry> getInbox(String tenancyId) {
    UUID caseId = caseResolver.resolve(tenancyId);
    return engineApi.getInbox(caseId, tenancyId);
  }

  public void resolveGate(
      String tenancyId,
      String entryId,
      ConductorInboxEntry.Status outcome,
      String reason,
      String feedback) {
    UUID caseId = caseResolver.resolve(tenancyId);
    engineApi.resolveGate(caseId, tenancyId, entryId, outcome, reason, feedback);
  }

  public DenyPatternView getDenyPatterns(String tenancyId) {
    UUID caseId = caseResolver.resolve(tenancyId);
    return engineApi.getDenyPatterns(caseId, tenancyId);
  }

  public void addDenyPattern(String tenancyId, String pattern) {
    UUID caseId = caseResolver.resolve(tenancyId);
    engineApi.addDenyPattern(caseId, tenancyId, pattern);
  }

  public void removeDenyPattern(String tenancyId, String pattern) {
    UUID caseId = caseResolver.resolve(tenancyId);
    engineApi.removeDenyPattern(caseId, tenancyId, pattern);
  }

  public List<WatchPattern> getWatchPatterns(String tenancyId) {
    UUID caseId = caseResolver.resolve(tenancyId);
    return engineApi.getWatchPatterns(caseId, tenancyId);
  }

  public void addWatchPattern(
      String tenancyId,
      String category,
      String areaId,
      String targetPattern,
      Integer minEstimatedSize) {
    UUID caseId = caseResolver.resolve(tenancyId);
    engineApi.addWatchPattern(caseId, tenancyId, category, areaId, targetPattern, minEstimatedSize);
  }

  public void removeWatchPattern(String tenancyId, String patternId) {
    UUID caseId = caseResolver.resolve(tenancyId);
    engineApi.removeWatchPattern(caseId, tenancyId, patternId);
  }

  public GatePolicy getGatePolicy(String tenancyId) {
    UUID caseId = caseResolver.resolve(tenancyId);
    return engineApi.getGatePolicy(caseId, tenancyId);
  }

  public void setGatePolicy(String tenancyId, GatePolicy policy) {
    UUID caseId = caseResolver.resolve(tenancyId);
    engineApi.setGatePolicy(caseId, tenancyId, policy);
  }

  public List<io.casehub.api.view.EvolutionStateSnapshot.ImprovementStreamView> getStreams(
      String tenancyId) {
    UUID caseId = caseResolver.resolve(tenancyId);
    return engineApi.getStreamProgress(caseId, tenancyId);
  }

  public void pauseCategory(String tenancyId, String category, int durationMinutes) {
    UUID caseId = caseResolver.resolve(tenancyId);
    engineApi.pauseCategory(caseId, tenancyId, category, durationMinutes);
  }

  public void unpauseCategory(String tenancyId, String category) {
    UUID caseId = caseResolver.resolve(tenancyId);
    engineApi.unpauseCategory(caseId, tenancyId, category);
  }

  public void resetCircuitBreaker(String tenancyId) {
    UUID caseId = caseResolver.resolve(tenancyId);
    engineApi.resetCircuitBreaker(caseId);
  }
}
