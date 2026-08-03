package io.casehub.devtown.app.governance;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.TrustScoreSnapshot;
import io.casehub.ledger.runtime.service.TrustGateService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrustQueryServiceTest {

    TrustGateService trustGateService;
    EntityManager em;
    TrustQueryService service;

    @BeforeEach
    void setUp() {
        trustGateService = mock(TrustGateService.class);
        em = mock(EntityManager.class);
        service = new TrustQueryService();
        service.trustGateService = trustGateService;
        service.em = em;
    }

    @Test
    void trustScore_assemblesGlobalAndCapabilityAndDimensionScores() {
        when(trustGateService.currentScore("agent-1")).thenReturn(OptionalDouble.of(0.72));
        when(trustGateService.allCapabilityScores("agent-1"))
                .thenReturn(Map.of("security-review", 0.75, "style-review", 0.60));
        when(trustGateService.allDimensionScores("agent-1"))
                .thenReturn(Map.of("review-thoroughness", 0.80));

        final var result = service.trustScore("agent-1");

        assertThat(result.actorId()).isEqualTo("agent-1");
        assertThat(result.globalScore()).isEqualTo(0.72);
        assertThat(result.capabilityScores()).containsEntry("security-review", 0.75);
        assertThat(result.dimensionScores()).containsEntry("review-thoroughness", 0.80);
    }

    @Test
    void trustScore_returnsNullGlobalForBootstrapAgent() {
        when(trustGateService.currentScore("new-agent")).thenReturn(OptionalDouble.empty());
        when(trustGateService.allCapabilityScores("new-agent")).thenReturn(Map.of());
        when(trustGateService.allDimensionScores("new-agent")).thenReturn(Map.of());

        final var result = service.trustScore("new-agent");

        assertThat(result.globalScore()).isNull();
        assertThat(result.capabilityScores()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void trustTrend_returnsSnapshotsFromNamedQuery() {
        final var snap1 = new TrustScoreSnapshot();
        snap1.occurredAt = Instant.parse("2026-08-01T10:00:00Z");
        snap1.score = 0.75;
        snap1.previousScore = 0.70;

        final var snap2 = new TrustScoreSnapshot();
        snap2.occurredAt = Instant.parse("2026-08-02T10:00:00Z");
        snap2.score = 0.78;
        snap2.previousScore = 0.75;

        final TypedQuery<TrustScoreSnapshot> mockQuery = mock(TypedQuery.class);
        when(em.createNamedQuery("TrustScoreSnapshot.findByActorAndCapability", TrustScoreSnapshot.class))
                .thenReturn(mockQuery);
        when(mockQuery.setParameter(anyString(), any())).thenReturn(mockQuery);
        when(mockQuery.setMaxResults(anyInt())).thenReturn(mockQuery);
        when(mockQuery.getResultList()).thenReturn(List.of(snap2, snap1));

        final var result = service.trustTrend("agent-1", "security-review", 30);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).score()).isEqualTo(0.78);
        assertThat(result.get(1).score()).isEqualTo(0.75);
    }

    @SuppressWarnings("unchecked")
    @Test
    void routingHistory_extractsPhaseAndScoreFromRationale() {
        final var entry = new WorkerDecisionEntry();
        entry.id = UUID.randomUUID();
        entry.occurredAt = Instant.now();
        entry.capabilityTag = "security-review";
        entry.workerId = "agent-1";
        entry.trustScoreAtRouting = 0.72;
        entry.routingRationale = """
                {"strategyId":"trust-weighted","selected":{"workerId":"agent-1",\
                "trustScore":0.72,"workloadScore":0.8,"phase":"QUALIFIED",\
                "observations":14,"finalScore":0.74},"alternatives":[],"policy":{}}""";

        final TypedQuery<WorkerDecisionEntry> mockQuery = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(WorkerDecisionEntry.class))).thenReturn(mockQuery);
        when(mockQuery.setParameter(anyString(), any())).thenReturn(mockQuery);
        when(mockQuery.setMaxResults(anyInt())).thenReturn(mockQuery);
        when(mockQuery.getResultList()).thenReturn(List.of(entry));

        final var result = service.routingHistory("agent-1", null, 50);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).phase()).isEqualTo("QUALIFIED");
        assertThat(result.get(0).finalScore()).isEqualTo(0.74);
    }

    @SuppressWarnings("unchecked")
    @Test
    void routingHistory_fallsBackToUnknownPhaseForNullRationale() {
        final var entry = new WorkerDecisionEntry();
        entry.id = UUID.randomUUID();
        entry.occurredAt = Instant.now();
        entry.capabilityTag = "style-review";
        entry.workerId = "agent-1";
        entry.trustScoreAtRouting = 0.55;
        entry.routingRationale = null;

        final TypedQuery<WorkerDecisionEntry> mockQuery = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(WorkerDecisionEntry.class))).thenReturn(mockQuery);
        when(mockQuery.setParameter(anyString(), any())).thenReturn(mockQuery);
        when(mockQuery.setMaxResults(anyInt())).thenReturn(mockQuery);
        when(mockQuery.getResultList()).thenReturn(List.of(entry));

        final var result = service.routingHistory("agent-1", null, 50);

        assertThat(result.get(0).phase()).isEqualTo("UNKNOWN");
        assertThat(result.get(0).finalScore()).isEqualTo(0.55);
    }

    @SuppressWarnings("unchecked")
    @Test
    void routingDetail_deserializesFullRationaleWithAlternatives() {
        final UUID entryId = UUID.randomUUID();
        final var entry = new WorkerDecisionEntry();
        entry.id = entryId;
        entry.workerId = "agent-1";
        entry.capabilityTag = "security-review";
        entry.routingRationale = """
                {
                  "strategyId": "trust-weighted",
                  "selected": {
                    "workerId": "agent-1", "trustScore": 0.72, "workloadScore": 0.8,
                    "phase": "QUALIFIED", "observations": 14, "finalScore": 0.74
                  },
                  "alternatives": [{
                    "workerId": "agent-2", "trustScore": 0.55, "workloadScore": 0.9,
                    "phase": "QUALIFIED", "observations": 12, "finalScore": 0.62,
                    "exclusionReason": null
                  }],
                  "policy": {
                    "threshold": 0.70, "borderlineMargin": 0.05, "blendFactor": 0.70,
                    "minimumObservations": 10, "qualityFloors": {"review-thoroughness": 0.60},
                    "cbrWeight": 0.0, "bootstrapEscalationRequired": false
                  }
                }""";

        when(em.find(WorkerDecisionEntry.class, entryId)).thenReturn(entry);

        @SuppressWarnings("unchecked")
        final TypedQuery<Object> genericQuery = mock(TypedQuery.class);
        when(em.createQuery(anyString(), any(Class.class))).thenReturn(genericQuery);
        when(genericQuery.setParameter(anyString(), any())).thenReturn(genericQuery);
        when(genericQuery.setMaxResults(anyInt())).thenReturn(genericQuery);
        when(genericQuery.getResultList()).thenReturn(List.of());

        final var detail = service.routingDetail("agent-1", entryId);

        assertThat(detail).isNotNull();
        assertThat(detail.rationale().strategyId()).isEqualTo("trust-weighted");
        assertThat(detail.rationale().selected().workerId()).isEqualTo("agent-1");
        assertThat(detail.rationale().selected().phase()).isEqualTo("QUALIFIED");
        assertThat(detail.rationale().alternatives()).hasSize(1);
        assertThat(detail.rationale().alternatives().get(0).workerId()).isEqualTo("agent-2");
        assertThat(detail.rationale().policy().threshold()).isEqualTo(0.70);
        assertThat(detail.rationale().policy().qualityFloors()).containsEntry("review-thoroughness", 0.60);
        assertThat(detail.feedback()).isEmpty();
    }

    @Test
    void routingDetail_returnsNullForNullRationale() {
        final UUID entryId = UUID.randomUUID();
        final var entry = new WorkerDecisionEntry();
        entry.id = entryId;
        entry.workerId = "agent-1";
        entry.routingRationale = null;

        when(em.find(WorkerDecisionEntry.class, entryId)).thenReturn(entry);

        assertThat(service.routingDetail("agent-1", entryId)).isNull();
    }

    @Test
    void routingDetail_returnsNullForCrossActorAccess() {
        final UUID entryId = UUID.randomUUID();
        final var entry = new WorkerDecisionEntry();
        entry.id = entryId;
        entry.workerId = "other-agent";

        when(em.find(WorkerDecisionEntry.class, entryId)).thenReturn(entry);

        assertThat(service.routingDetail("agent-1", entryId)).isNull();
    }

    @Test
    void routingDetail_returnsNullForUnknownEntry() {
        final UUID entryId = UUID.randomUUID();
        when(em.find(WorkerDecisionEntry.class, entryId)).thenReturn(null);

        assertThat(service.routingDetail("agent-1", entryId)).isNull();
    }

    @Test
    void routingDetail_returnsNullForMalformedJson() {
        final UUID entryId = UUID.randomUUID();
        final var entry = new WorkerDecisionEntry();
        entry.id = entryId;
        entry.workerId = "agent-1";
        entry.routingRationale = "not valid json {{{";

        when(em.find(WorkerDecisionEntry.class, entryId)).thenReturn(entry);

        assertThat(service.routingDetail("agent-1", entryId)).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void routingDetail_mapsFeedbackWithTrustBeforeAfter() {
        final UUID entryId = UUID.randomUUID();
        final var entry = new WorkerDecisionEntry();
        entry.id = entryId;
        entry.workerId = "agent-1";
        entry.capabilityTag = "security-review";
        entry.routingRationale = """
                {"strategyId":"trust-weighted","selected":{"workerId":"agent-1",\
                "trustScore":0.72,"workloadScore":0.8,"phase":"QUALIFIED",\
                "observations":14,"finalScore":0.74},"alternatives":[],"policy":{}}""";

        final var attestation = new LedgerAttestation();
        attestation.verdict = AttestationVerdict.FLAGGED;
        attestation.attestorId = "devtown:incident-feedback";
        attestation.evidence = "Incident INC-001: Security bypass";
        attestation.occurredAt = Instant.parse("2026-08-02T15:00:00Z");
        attestation.trustDimension = "review-thoroughness";

        final var snapshot = new TrustScoreSnapshot();
        snapshot.previousScore = 0.75;
        snapshot.score = 0.65;

        when(em.find(WorkerDecisionEntry.class, entryId)).thenReturn(entry);

        @SuppressWarnings("unchecked")
        final TypedQuery<Object> attestationQuery = mock(TypedQuery.class, "attestationQuery");
        @SuppressWarnings("unchecked")
        final TypedQuery<Object> snapshotQuery = mock(TypedQuery.class, "snapshotQuery");

        when(em.createQuery(anyString(), any(Class.class)))
                .thenReturn(attestationQuery)
                .thenReturn(snapshotQuery);
        when(attestationQuery.setParameter(anyString(), any())).thenReturn(attestationQuery);
        when(attestationQuery.getResultList()).thenReturn(List.of(attestation));
        when(snapshotQuery.setParameter(anyString(), any())).thenReturn(snapshotQuery);
        when(snapshotQuery.setMaxResults(anyInt())).thenReturn(snapshotQuery);
        when(snapshotQuery.getResultList()).thenReturn(List.of(snapshot));

        final var detail = service.routingDetail("agent-1", entryId);

        assertThat(detail.feedback()).hasSize(1);
        final var gate = detail.feedback().get(0);
        assertThat(gate.decision()).isEqualTo("FLAGGED");
        assertThat(gate.actor()).isEqualTo("devtown:incident-feedback");
        assertThat(gate.trustScoreBefore()).isEqualTo(0.75);
        assertThat(gate.trustScoreAfter()).isEqualTo(0.65);
        assertThat(gate.dimension()).isEqualTo("review-thoroughness");
    }
}
