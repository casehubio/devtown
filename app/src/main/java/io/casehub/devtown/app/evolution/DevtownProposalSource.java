package io.casehub.devtown.app.evolution;

import io.casehub.api.model.improvement.ImprovementConfig;
import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.model.stigmergy.CapabilityAreaAssessment;
import io.casehub.api.spi.improvement.CapabilityArea;
import io.casehub.api.spi.improvement.ImprovementProposalSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class DevtownProposalSource implements ImprovementProposalSource {

  private static final double HEALTH_THRESHOLD = 0.6;

  private final ReviewerTrustCapabilityArea reviewerTrust;
  private final SlaComplianceCapabilityArea slaCompliance;
  private final MergeQueueHealthCapabilityArea mergeQueueHealth;
  private final CiReliabilityCapabilityArea ciReliability;
  private final ReviewQualityCapabilityArea reviewQuality;

  @Inject
  public DevtownProposalSource(
      ReviewerTrustCapabilityArea reviewerTrust,
      SlaComplianceCapabilityArea slaCompliance,
      MergeQueueHealthCapabilityArea mergeQueueHealth,
      CiReliabilityCapabilityArea ciReliability,
      ReviewQualityCapabilityArea reviewQuality) {
    this.reviewerTrust = reviewerTrust;
    this.slaCompliance = slaCompliance;
    this.mergeQueueHealth = mergeQueueHealth;
    this.ciReliability = ciReliability;
    this.reviewQuality = reviewQuality;
  }

  @Override
  public String sourceId() {
    return "devtown-assessment";
  }

  @Override
  public String domainId() {
    return "devtown";
  }

  @Override
  public List<ImprovementRequest> propose(
      UUID caseId, String tenancyId, ImprovementConfig config) {
    List<ImprovementRequest> proposals = new ArrayList<>();

    checkAndPropose(proposals, reviewerTrust, caseId, tenancyId,
        "recalibrate-trust-weights", "reviewer-calibration", "trust-drift");
    checkAndPropose(proposals, slaCompliance, caseId, tenancyId,
        "adjust-sla-thresholds", "sla-tuning", "sla-drift");
    checkAndPropose(proposals, mergeQueueHealth, caseId, tenancyId,
        "optimize-queue-batching", "queue-optimization", "queue-degradation");
    checkAndPropose(proposals, ciReliability, caseId, tenancyId,
        "tighten-ci-gates", "gate-tightening", "ci-degradation");
    checkAndPropose(proposals, reviewQuality, caseId, tenancyId,
        "adjust-review-routing", "routing-adjustment", "review-quality-drift");

    return proposals;
  }

  private void checkAndPropose(
      List<ImprovementRequest> proposals,
      CapabilityArea area,
      UUID caseId,
      String tenancyId,
      String improvementType,
      String category,
      String trigger) {
    CapabilityAreaAssessment assessment = area.assess(caseId, tenancyId);
    if (assessment.healthScore() < HEALTH_THRESHOLD) {
      proposals.add(new ImprovementRequest(
          improvementType,
          category,
          area.id(),
          3,
          Map.of(
              "trigger", trigger,
              "current-score", String.valueOf(assessment.healthScore())),
          "devtown"));
    }
  }
}
