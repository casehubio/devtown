package io.casehub.devtown.app.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.improvement.ImprovementConfig;
import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.model.stigmergy.CapabilityAreaAssessment;
import io.casehub.api.model.stigmergy.CapabilityAreaAssessment.LandscapePosition;
import io.casehub.engine.common.spi.EventLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DevtownProposalSourceTest {

  private final UUID caseId = UUID.randomUUID();
  private final String tenancyId = "test-tenant";
  private final ImprovementConfig config =
      new ImprovementConfig(null, null, null, null, null);

  private EventLogRepository eventLog;
  private ReviewerTrustCapabilityArea reviewerTrust;
  private SlaComplianceCapabilityArea slaCompliance;
  private MergeQueueHealthCapabilityArea mergeQueueHealth;
  private CiReliabilityCapabilityArea ciReliability;
  private ReviewQualityCapabilityArea reviewQuality;
  private DevtownProposalSource source;

  @BeforeEach
  void setUp() {
    eventLog = Mockito.mock(EventLogRepository.class);
    reviewerTrust = Mockito.spy(new ReviewerTrustCapabilityArea(eventLog));
    slaCompliance = Mockito.spy(new SlaComplianceCapabilityArea(eventLog));
    mergeQueueHealth = Mockito.spy(new MergeQueueHealthCapabilityArea(eventLog));
    ciReliability = Mockito.spy(new CiReliabilityCapabilityArea(eventLog));
    reviewQuality = Mockito.spy(new ReviewQualityCapabilityArea(eventLog));
    source = new DevtownProposalSource(
        reviewerTrust, slaCompliance, mergeQueueHealth, ciReliability, reviewQuality);
  }

  @Test
  void sourceId_returns_devtown_assessment() {
    assertThat(source.sourceId()).isEqualTo("devtown-assessment");
  }

  @Test
  void domainId_returns_devtown() {
    assertThat(source.domainId()).isEqualTo("devtown");
  }

  @Test
  void no_proposals_when_all_areas_healthy() {
    stubAssessment(reviewerTrust, "reviewer-trust", 0.8);
    stubAssessment(slaCompliance, "sla-compliance", 0.9);
    stubAssessment(mergeQueueHealth, "merge-queue-health", 0.7);
    stubAssessment(ciReliability, "ci-reliability", 0.85);
    stubAssessment(reviewQuality, "review-quality", 0.75);

    List<ImprovementRequest> proposals = source.propose(caseId, tenancyId, config);
    assertThat(proposals).isEmpty();
  }

  @Test
  void proposal_when_reviewer_trust_degrades() {
    stubAssessment(reviewerTrust, "reviewer-trust", 0.4);
    stubAssessment(slaCompliance, "sla-compliance", 0.9);
    stubAssessment(mergeQueueHealth, "merge-queue-health", 0.8);
    stubAssessment(ciReliability, "ci-reliability", 0.85);
    stubAssessment(reviewQuality, "review-quality", 0.75);

    List<ImprovementRequest> proposals = source.propose(caseId, tenancyId, config);
    assertThat(proposals).hasSize(1);
    assertThat(proposals.get(0).category()).isEqualTo("reviewer-calibration");
    assertThat(proposals.get(0).domainId()).isEqualTo("devtown");
  }

  @Test
  void proposal_when_sla_compliance_degrades() {
    stubAssessment(reviewerTrust, "reviewer-trust", 0.8);
    stubAssessment(slaCompliance, "sla-compliance", 0.3);
    stubAssessment(mergeQueueHealth, "merge-queue-health", 0.8);
    stubAssessment(ciReliability, "ci-reliability", 0.85);
    stubAssessment(reviewQuality, "review-quality", 0.75);

    List<ImprovementRequest> proposals = source.propose(caseId, tenancyId, config);
    assertThat(proposals).hasSize(1);
    assertThat(proposals.get(0).category()).isEqualTo("sla-tuning");
  }

  @Test
  void proposal_when_merge_queue_degrades() {
    stubAssessment(reviewerTrust, "reviewer-trust", 0.8);
    stubAssessment(slaCompliance, "sla-compliance", 0.8);
    stubAssessment(mergeQueueHealth, "merge-queue-health", 0.3);
    stubAssessment(ciReliability, "ci-reliability", 0.85);
    stubAssessment(reviewQuality, "review-quality", 0.75);

    List<ImprovementRequest> proposals = source.propose(caseId, tenancyId, config);
    assertThat(proposals).hasSize(1);
    assertThat(proposals.get(0).category()).isEqualTo("queue-optimization");
  }

  @Test
  void multiple_proposals_when_multiple_areas_degrade() {
    stubAssessment(reviewerTrust, "reviewer-trust", 0.3);
    stubAssessment(slaCompliance, "sla-compliance", 0.4);
    stubAssessment(mergeQueueHealth, "merge-queue-health", 0.2);
    stubAssessment(ciReliability, "ci-reliability", 0.85);
    stubAssessment(reviewQuality, "review-quality", 0.75);

    List<ImprovementRequest> proposals = source.propose(caseId, tenancyId, config);
    assertThat(proposals).hasSize(3);
    assertThat(proposals).extracting(ImprovementRequest::category)
        .containsExactlyInAnyOrder(
            "reviewer-calibration", "sla-tuning", "queue-optimization");
  }

  @Test
  void no_proposal_at_exact_threshold() {
    stubAssessment(reviewerTrust, "reviewer-trust", 0.6);
    stubAssessment(slaCompliance, "sla-compliance", 0.6);
    stubAssessment(mergeQueueHealth, "merge-queue-health", 0.6);
    stubAssessment(ciReliability, "ci-reliability", 0.6);
    stubAssessment(reviewQuality, "review-quality", 0.6);

    List<ImprovementRequest> proposals = source.propose(caseId, tenancyId, config);
    assertThat(proposals).isEmpty();
  }

  @Test
  void proposal_metadata_contains_trigger_and_score() {
    stubAssessment(reviewerTrust, "reviewer-trust", 0.4);
    stubAssessment(slaCompliance, "sla-compliance", 0.9);
    stubAssessment(mergeQueueHealth, "merge-queue-health", 0.8);
    stubAssessment(ciReliability, "ci-reliability", 0.85);
    stubAssessment(reviewQuality, "review-quality", 0.75);

    List<ImprovementRequest> proposals = source.propose(caseId, tenancyId, config);
    assertThat(proposals.get(0).metadata()).containsKey("trigger");
    assertThat(proposals.get(0).metadata()).containsKey("current-score");
  }

  private void stubAssessment(
      Object area, String areaId, double healthScore) {
    var assessment = new CapabilityAreaAssessment(
        areaId, healthScore,
        healthScore < 0.4 ? LandscapePosition.BEHIND
            : healthScore < 0.7 ? LandscapePosition.AT_PARITY : LandscapePosition.AHEAD,
        1.0 - healthScore, healthScore < 0.5 ? 0.7 : 0.3, 0.0, Instant.now());
    if (area instanceof ReviewerTrustCapabilityArea a) {
      Mockito.doReturn(assessment).when(a).assess(caseId, tenancyId);
    } else if (area instanceof SlaComplianceCapabilityArea a) {
      Mockito.doReturn(assessment).when(a).assess(caseId, tenancyId);
    } else if (area instanceof MergeQueueHealthCapabilityArea a) {
      Mockito.doReturn(assessment).when(a).assess(caseId, tenancyId);
    } else if (area instanceof CiReliabilityCapabilityArea a) {
      Mockito.doReturn(assessment).when(a).assess(caseId, tenancyId);
    } else if (area instanceof ReviewQualityCapabilityArea a) {
      Mockito.doReturn(assessment).when(a).assess(caseId, tenancyId);
    }
  }
}
