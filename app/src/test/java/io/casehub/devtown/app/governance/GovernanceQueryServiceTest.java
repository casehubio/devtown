package io.casehub.devtown.app.governance;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.devtown.app.MergeQueueService;
import io.casehub.devtown.app.mcp.PrReviewCaseTracker;
import io.casehub.devtown.app.mcp.TrackedEvent;
import io.casehub.devtown.domain.preferences.IntPreference;
import io.casehub.devtown.domain.sla.SlaPreferenceKeys;
import io.casehub.devtown.review.PrPayload;
import io.casehub.devtown.review.sla.SlaCalibrationRecord;
import io.casehub.devtown.review.sla.SlaCalibrationStore;
import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.ledger.runtime.service.federation.ActorExport;
import io.casehub.ledger.runtime.service.federation.TrustExportPayload;
import io.casehub.ledger.runtime.service.federation.TrustExportService;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.store.CommitmentStore;
import io.casehub.work.runtime.repository.WorkItemStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GovernanceQueryServiceTest {

    PrReviewCaseTracker tracker;
    CommitmentStore commitmentStore;
    TrustExportService trustExportService;
    TrustGateService trustGateService;
    WorkItemStore workItemStore;
    MergeQueueService mergeQueueService;
    CaseHubRuntime caseHubRuntime;
    SlaCalibrationStore slaCalibrationStore;
    PreferenceProvider preferenceProvider;
    Preferences prefs;

    GovernanceQueryService service;

    @BeforeEach
    void setUp() {
        tracker = new PrReviewCaseTracker();
        commitmentStore = mock(CommitmentStore.class);
        trustExportService = mock(TrustExportService.class);
        trustGateService = mock(TrustGateService.class);
        workItemStore = mock(WorkItemStore.class);
        mergeQueueService = mock(MergeQueueService.class);
        caseHubRuntime = mock(CaseHubRuntime.class);
        slaCalibrationStore = mock(SlaCalibrationStore.class);
        preferenceProvider = mock(PreferenceProvider.class);
        prefs = mock(Preferences.class);

        service = new GovernanceQueryService(
            tracker, commitmentStore, trustExportService, trustGateService,
            workItemStore, mergeQueueService, caseHubRuntime,
            slaCalibrationStore, preferenceProvider
        );
    }

    @Test
    void queueStatus_returnsActiveReviewsWithStatusCounts() {
        var payload = new PrPayload("casehubio/devtown", 42, "abc123", "main", 150, "jsmith", 0L, List.of("src/Main.java"));
        var caseId = UUID.randomUUID();
        tracker.register(caseId, "default", payload);

        var result = service.queueStatus();

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.reviews()).hasSize(1);
        assertThat(result.reviews().get(0).prNumber()).isEqualTo(42);
        assertThat(result.reviews().get(0).contributor()).isEqualTo("jsmith");
        assertThat(result.countsByStatus()).containsKey("RUNNING");
    }

    @Test
    void recentEvents_returnsEventsFromTrackerBuffer() {
        var event = new TrackedEvent(Instant.now(), UUID.randomUUID(), "casehubio/devtown", 42, "COMPLETED", "COMPLETED", "agent-1");
        tracker.addEvent(event);

        var result = service.recentEvents(10, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).repo()).isEqualTo("casehubio/devtown");
    }

    @Test
    void systemHealth_calculatesFleetSizeAndAverageTrust() {
        // Fleet with 2 agents
        var actor1 = new ActorExport("agent-1", ActorType.AGENT, null, List.of(), List.of(), List.of());
        var actor2 = new ActorExport("agent-2", ActorType.AGENT, null, List.of(), List.of(), List.of());
        when(trustExportService.exportAll(0.0)).thenReturn(new TrustExportPayload(Instant.now(), "test-deployment", List.of(actor1, actor2)));

        // agent-1 has code-analysis trust
        when(trustGateService.allCapabilityScores("agent-1")).thenReturn(Map.of("code-analysis", 0.8));
        when(trustGateService.allCapabilityScores("agent-2")).thenReturn(Map.of("code-analysis", 0.6));

        when(commitmentStore.findAllOpen()).thenReturn(List.of());
        when(workItemStore.scan(any())).thenReturn(List.of());

        var result = service.systemHealth();

        assertThat(result.fleetSize()).isEqualTo(2);
        assertThat(result.avgTrustByCapability()).containsEntry("code-analysis", 0.7);
    }

    @Test
    void reviewerHealth_returnsCommitmentsAndTrustScores() {
        when(commitmentStore.findOpenByObligor("agent-1")).thenReturn(List.of(
            mock(Commitment.class), mock(Commitment.class)
        ));
        when(trustGateService.allCapabilityScores("agent-1")).thenReturn(Map.of("code-analysis", 0.85));
        when(trustGateService.allDimensionScores("agent-1")).thenReturn(Map.of("review-thoroughness", 0.90));
        when(trustGateService.decisionCount(eq("agent-1"), any())).thenReturn(5);

        var result = service.reviewerHealth("agent-1");

        assertThat(result.reviewerId()).isEqualTo("agent-1");
        assertThat(result.openCommitments()).isEqualTo(2);
        assertThat(result.trustByCapability()).containsEntry("code-analysis", 0.85);
    }

    @Test
    void slaComparison_withCalibrationData() {
        var now = Instant.now();
        var records = List.of(
                new SlaCalibrationRecord(UUID.randomUUID(), "pr-review", "casehubio/devtown/pr-review",
                                         Duration.ofHours(18), Duration.ofHours(12), Duration.ofHours(30), 10, UUID.randomUUID(), now),
                new SlaCalibrationRecord(UUID.randomUUID(), "code-analysis", "casehubio/devtown/pr-review",
                                         Duration.ofHours(2), Duration.ofHours(1), Duration.ofHours(5), 8, UUID.randomUUID(), now)
                             );
        when(slaCalibrationStore.findLatestCalibration("casehubio/devtown/pr-review")).thenReturn(records);
        when(preferenceProvider.resolve(any())).thenReturn(prefs);
        when(prefs.getOrDefault(SlaPreferenceKeys.COMPLETION_HOURS)).thenReturn(IntPreference.of(24));

        var result = service.slaComparison();

        assertThat(result.entries()).hasSize(2);
        assertThat(result.calibratedAt()).isEqualTo(now);

        var overall = result.entries().get(0);
        assertThat(overall.capability()).isEqualTo("pr-review");
        assertThat(overall.configuredSeconds()).isEqualTo(Duration.ofHours(24).toSeconds());
        assertThat(overall.estimatedMedianSeconds()).isEqualTo(Duration.ofHours(18).toSeconds());
        assertThat(overall.deviationPercent()).isEqualTo(-25.0);

        var codeAnalysis = result.entries().get(1);
        assertThat(codeAnalysis.capability()).isEqualTo("code-analysis");
        assertThat(codeAnalysis.estimatedMedianSeconds()).isEqualTo(Duration.ofHours(2).toSeconds());
    }

    @Test
    void slaComparison_noCalibrationData() {
        when(slaCalibrationStore.findLatestCalibration("casehubio/devtown/pr-review")).thenReturn(List.of());
        when(preferenceProvider.resolve(any())).thenReturn(prefs);
        when(prefs.getOrDefault(SlaPreferenceKeys.COMPLETION_HOURS)).thenReturn(IntPreference.of(24));

        var result = service.slaComparison();

        assertThat(result.entries()).isEmpty();
        assertThat(result.calibratedAt()).isNull();
    }

    @Test
    void slaComparison_positiveDeviationWhenEstimateExceedsConfigured() {
        var now = Instant.now();
        var records = List.of(
                new SlaCalibrationRecord(UUID.randomUUID(), "pr-review", "casehubio/devtown/pr-review",
                                         Duration.ofHours(30), Duration.ofHours(20), Duration.ofHours(48), 15, UUID.randomUUID(), now)
                             );
        when(slaCalibrationStore.findLatestCalibration("casehubio/devtown/pr-review")).thenReturn(records);
        when(preferenceProvider.resolve(any())).thenReturn(prefs);
        when(prefs.getOrDefault(SlaPreferenceKeys.COMPLETION_HOURS)).thenReturn(IntPreference.of(24));

        var result = service.slaComparison();

        assertThat(result.entries()).hasSize(1);
        assertThat(result.entries().get(0).deviationPercent()).isEqualTo(25.0);
    }


}
