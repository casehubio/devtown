package io.casehub.devtown.app.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.ledger.runtime.model.TrustScoreSnapshot;
import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;
import io.casehub.ledger.runtime.service.TrustGateService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TrustQueryService {

    private static final Logger LOG = Logger.getLogger(TrustQueryService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject TrustGateService trustGateService;
    @Inject @LedgerPersistenceUnit EntityManager em;

    public record TrustScoreResponse(String actorId, Double globalScore,
        Map<String, Double> capabilityScores, Map<String, Double> dimensionScores) {}

    public record TrustTrendPoint(Instant timestamp, double score, double previousScore) {}

    public record RoutingDecisionSummary(UUID id, Instant timestamp, String capabilityTag,
        String selectedWorkerId, double finalScore, String phase) {}

    public record RoutingDecisionDetail(RoutingRationale rationale, List<GateDecision> feedback) {}

    public record RoutingRationale(String capabilityTag, String strategyId,
        CandidateScore selected, List<CandidateScore> alternatives, RoutingPolicySummary policy) {}

    public record CandidateScore(String workerId, Double trustScore, double workloadScore,
        String phase, int observations, double finalScore, String exclusionReason) {}

    public record RoutingPolicySummary(double threshold, double borderlineMargin, double blendFactor,
        int minimumObservations, Map<String, Double> qualityFloors, double cbrWeight,
        boolean bootstrapEscalationRequired) {}

    public record GateDecision(String decision, String actor, String attestation,
        double trustScoreBefore, double trustScoreAfter, String dimension) {}

    @Transactional
    public TrustScoreResponse trustScore(String actorId) {
        final var globalOpt = trustGateService.currentScore(actorId);
        Double globalScore = globalOpt.isPresent() ? globalOpt.getAsDouble() : null;
        Map<String, Double> capabilityScores = trustGateService.allCapabilityScores(actorId);
        Map<String, Double> dimensionScores = trustGateService.allDimensionScores(actorId);
        return new TrustScoreResponse(actorId, globalScore, capabilityScores, dimensionScores);
    }

    @Transactional
    public List<TrustTrendPoint> trustTrend(String actorId, String capabilityTag, int limit) {
        List<TrustScoreSnapshot> snapshots = em
                .createNamedQuery("TrustScoreSnapshot.findByActorAndCapability", TrustScoreSnapshot.class)
                .setParameter("actorId", actorId)
                .setParameter("capabilityTag", capabilityTag)
                .setMaxResults(Math.min(limit, 200))
                .getResultList();
        return snapshots.stream()
                .map(s -> new TrustTrendPoint(s.occurredAt, s.score, s.previousScore))
                .toList();
    }

    @Transactional
    public List<RoutingDecisionSummary> routingHistory(String actorId, String capabilityTag, int limit) {
        String jpql = "SELECT e FROM WorkerDecisionEntry e WHERE e.workerId = :actorId";
        if (capabilityTag != null) {
            jpql += " AND e.capabilityTag = :capabilityTag";
        }
        jpql += " ORDER BY e.occurredAt DESC";

        var query = em.createQuery(jpql, WorkerDecisionEntry.class)
                .setParameter("actorId", actorId)
                .setMaxResults(Math.min(limit, 200));
        if (capabilityTag != null) {
            query.setParameter("capabilityTag", capabilityTag);
        }

        return query.getResultList().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public RoutingDecisionDetail routingDetail(String actorId, UUID entryId) {
        WorkerDecisionEntry entry = em.find(WorkerDecisionEntry.class, entryId);
        if (entry == null || !actorId.equals(entry.workerId)) {
            return null;
        }
        if (entry.routingRationale == null) {
            return null;
        }

        RoutingRationale rationale;
        try {
            rationale = parseRationale(entry);
        } catch (Exception e) {
            LOG.warnf("Failed to deserialize routing_rationale for entryId=%s: %s", entryId, e.getMessage());
            return null;
        }

        List<GateDecision> feedback = loadFeedback(entryId);
        return new RoutingDecisionDetail(rationale, feedback);
    }

    private RoutingDecisionSummary toSummary(WorkerDecisionEntry entry) {
        String phase = "UNKNOWN";
        double finalScore = entry.trustScoreAtRouting != null ? entry.trustScoreAtRouting : 0.0;

        if (entry.routingRationale != null) {
            try {
                JsonNode root = MAPPER.readTree(entry.routingRationale);
                JsonNode selected = root.path("selected");
                if (!selected.isMissingNode()) {
                    phase = selected.path("phase").asText("UNKNOWN");
                    finalScore = selected.path("finalScore").asDouble(finalScore);
                }
            } catch (Exception e) {
                LOG.debugf("Skipping rationale parse in summary for entryId=%s: %s", entry.id, e.getMessage());
            }
        }

        return new RoutingDecisionSummary(
                entry.id, entry.occurredAt, entry.capabilityTag,
                entry.workerId, finalScore, phase);
    }

    private RoutingRationale parseRationale(WorkerDecisionEntry entry) throws Exception {
        JsonNode root = MAPPER.readTree(entry.routingRationale);
        String strategyId = root.path("strategyId").asText("");
        CandidateScore selected = parseCandidate(root.path("selected"));
        List<CandidateScore> alternatives = new ArrayList<>();
        for (JsonNode alt : root.path("alternatives")) {
            alternatives.add(parseCandidate(alt));
        }
        RoutingPolicySummary policy = parsePolicy(root.path("policy"));
        return new RoutingRationale(entry.capabilityTag, strategyId, selected, alternatives, policy);
    }

    private CandidateScore parseCandidate(JsonNode node) {
        return new CandidateScore(
                node.path("workerId").asText(""),
                node.has("trustScore") && !node.get("trustScore").isNull()
                        ? node.get("trustScore").asDouble() : null,
                node.path("workloadScore").asDouble(0),
                node.path("phase").asText("UNKNOWN"),
                node.path("observations").asInt(0),
                node.path("finalScore").asDouble(0),
                node.has("exclusionReason") && !node.get("exclusionReason").isNull()
                        ? node.get("exclusionReason").asText() : null);
    }

    private RoutingPolicySummary parsePolicy(JsonNode node) {
        Map<String, Double> floors = new java.util.LinkedHashMap<>();
        node.path("qualityFloors").properties().forEach(e -> floors.put(e.getKey(), e.getValue().asDouble()));
        return new RoutingPolicySummary(
                node.path("threshold").asDouble(0),
                node.path("borderlineMargin").asDouble(0),
                node.path("blendFactor").asDouble(0),
                node.path("minimumObservations").asInt(0),
                floors,
                node.path("cbrWeight").asDouble(0),
                node.path("bootstrapEscalationRequired").asBoolean(false));
    }

    @SuppressWarnings("unchecked")
    private List<GateDecision> loadFeedback(UUID entryId) {
        List<LedgerAttestation> attestations = em
                .createQuery("SELECT a FROM LedgerAttestation a WHERE a.ledgerEntryId = :entryId", LedgerAttestation.class)
                .setParameter("entryId", entryId)
                .getResultList();

        if (attestations.isEmpty()) {
            return Collections.emptyList();
        }

        return attestations.stream().map(a -> {
            double before = 0.0;
            double after = 0.0;
            List<TrustScoreSnapshot> snapshots = em
                    .createQuery("SELECT s FROM TrustScoreSnapshot s WHERE s.actorId = :actorId"
                            + " AND s.occurredAt <= :ts ORDER BY s.occurredAt DESC", TrustScoreSnapshot.class)
                    .setParameter("actorId", a.attestorId)
                    .setParameter("ts", a.occurredAt)
                    .setMaxResults(1)
                    .getResultList();
            if (!snapshots.isEmpty()) {
                before = snapshots.get(0).previousScore;
                after = snapshots.get(0).score;
            }
            return new GateDecision(
                    a.verdict != null ? a.verdict.name() : "",
                    a.attestorId,
                    a.evidence,
                    before, after,
                    a.trustDimension != null ? a.trustDimension : "");
        }).toList();
    }
}
