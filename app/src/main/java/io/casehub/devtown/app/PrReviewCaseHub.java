package io.casehub.devtown.app;

import io.casehub.api.model.CaseDefinition;
import io.casehub.devtown.domain.MergeClient;
import io.casehub.devtown.domain.MergeOutcome;
import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.domain.ReviewFinding;
import io.casehub.devtown.review.CodeAnalysisAgent;
import io.casehub.devtown.review.CodeAnalysisResult;
import io.casehub.devtown.review.PrDiff;
import io.casehub.devtown.review.PrDiffCache;
import io.casehub.devtown.review.PrPayload;
import io.casehub.devtown.review.ReviewContext;
import io.casehub.devtown.review.ReviewerAgentRegistry;
import io.casehub.devtown.review.ReviewerOutcome;
import io.casehub.devtown.template.PrReviewTemplateCaseHub;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.Map;

@ApplicationScoped
public class PrReviewCaseHub extends PrReviewTemplateCaseHub {

    @Inject
    MergeClient                 mergeClient;
    @Inject
    ReviewerAgentRegistry       registry;
    @Inject
    Instance<CodeAnalysisAgent> codeAnalysisAgents;
    @Inject
    PrDiffCache                 diffCache;

    public PrReviewCaseHub() {
        super("devtown/pr-review-overrides.yaml");
    }

    @Override
    protected void augment(CaseDefinition definition) {
        definition.getWorkers().add(Worker.builder()
                                          .name("merge-executor")
                                          .capabilityName("merge-executor")
                                          .function(this::adaptMerge)
                                          .build());

        definition.getWorkers().add(Worker.builder()
                                          .name("code-analyzer")
                                          .capabilityName(ReviewDomain.CODE_ANALYSIS)
                                          .function(this::adaptCodeAnalysis)
                                          .build());

        for (String capability : ReviewDomain.FINDINGS_CAPABILITIES) {
            definition.getWorkers().add(Worker.builder()
                                              .name("reviewer-" + capability)
                                              .capabilityName(capability)
                                              .function(input -> adaptReview(capability, input))
                                              .build());
        }
    }

    WorkerResult adaptMerge(Map<String, Object> input) {
        @SuppressWarnings("unchecked")
        Map<String, Object> pr = (Map<String, Object>) input.get("pr");
        String   repo     = (String) pr.get("repo");
        String[] parts    = repo.split("/");
        int      prNumber = Integer.parseInt(String.valueOf(pr.get("id")));
        String   headSha  = (String) pr.get("headSha");

        return switch (mergeClient.merge(parts[0], parts[1], prNumber, headSha)) {
            case MergeOutcome.Success s -> WorkerResult.of(Map.of("merge_sha", s.mergeSha()));
            case MergeOutcome.Failure f -> WorkerResult.failed(f.reason());
        };
    }

    @SuppressWarnings("unchecked")
    WorkerResult adaptReview(String capability, Map<String, Object> input) {
        try {
            var agent = registry.forCapability(capability);
            if (agent.isEmpty()) {
                return WorkerResult.failed("no agent registered for " + capability);
            }

            ReviewContext context = buildContext(input);

            return switch (agent.get().handle(context)) {
                case ReviewerOutcome.Completed c -> {
                    String verdict = c.findings().stream()
                                      .anyMatch(f -> f.severity() == ReviewFinding.Severity.CRITICAL
                                                     || f.severity() == ReviewFinding.Severity.HIGH)
                                     ? "REJECTED" : "APPROVED";
                    yield WorkerResult.of(Map.of(
                            "outcome", verdict,
                            "findings", c.findings().stream()
                                         .map(f -> Map.of(
                                                 "severity", f.severity().name(),
                                                 "category", f.category(),
                                                 "filePath", f.filePath(),
                                                 "message", f.message(),
                                                 "confidence", f.confidence()))
                                         .toList()));
                }
                case ReviewerOutcome.Declined d -> WorkerResult.of(Map.of("outcome", "APPROVED"));
                case ReviewerOutcome.Failed f -> WorkerResult.failed(f.reason());
            };
        } catch (Exception e) {
            return WorkerResult.failed(capability + " adapter error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    WorkerResult adaptCodeAnalysis(Map<String, Object> input) {
        try {
            CodeAnalysisAgent agent = codeAnalysisAgents.stream()
                                                        .max(Comparator.comparingInt(CodeAnalysisAgent::priority))
                                                        .orElseThrow(() -> new IllegalStateException("no CodeAnalysisAgent registered"));

            ReviewContext      context = buildContext(input);
            CodeAnalysisResult result  = agent.analyse(context);

            return WorkerResult.of(Map.of(
                    "complete", result.complete(),
                    "securitySensitive", result.securitySensitive(),
                    "architectureCrossing", result.architectureCrossing(),
                    "scope", result.scope(),
                    "flaggedFiles", result.flaggedFiles(),
                    "crossingPoints", result.crossingPoints()));
        } catch (Exception e) {
            return WorkerResult.failed("code-analysis adapter error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private ReviewContext buildContext(Map<String, Object> input) {
        Map<String, Object> prMap    = (Map<String, Object>) input.get("pr");
        String              repo     = (String) prMap.get("repo");
        int                 prNumber = Integer.parseInt(String.valueOf(prMap.get("id")));
        String              headSha  = (String) prMap.get("headSha");

        PrDiff    diff = diffCache.get(repo, prNumber, headSha);
        PrPayload pr   = PrPayload.fromContextMap(prMap);
        return new ReviewContext(pr, diff);
    }
}
