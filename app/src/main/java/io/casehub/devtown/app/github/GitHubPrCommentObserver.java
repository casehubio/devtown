package io.casehub.devtown.app.github;

import io.casehub.devtown.domain.ReviewFinding;
import io.casehub.devtown.github.GitHubPullRequestApi;
import io.casehub.devtown.review.OutputMappingKeys;
import io.casehub.devtown.review.PrPayload;
import io.casehub.devtown.review.ReviewCompletedEvent;
import io.casehub.engine.common.spi.CrossTenantCaseInstanceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class GitHubPrCommentObserver {

    private static final Logger LOG = Logger.getLogger(GitHubPrCommentObserver.class);

    @Inject @RestClient
    GitHubPullRequestApi prApi;

    @Inject
    CrossTenantCaseInstanceRepository caseInstanceRepository;

    @ConfigProperty(name = "devtown.github.write-back.enabled", defaultValue = "false")
    boolean enabled;

    void onReviewCompleted(@ObservesAsync ReviewCompletedEvent event) {
        if (!enabled) {
            LOG.infof("[dev-mode] Would post PR review for %s#%d capability=%s",
                event.pr().repo(), event.pr().prNumber(), event.capability());
            return;
        }

        try {
            List<ReviewFinding> findings = extractFindings(event);
            if (findings.isEmpty()) return;

            PrPayload pr = event.pr();
            String[] repoParts = pr.repo().split("/");

            List<Map<String, Object>> comments = findings.stream()
                .filter(f -> f.filePath() != null)
                .map(f -> {
                    var c = new LinkedHashMap<String, Object>();
                    c.put("path", f.filePath());
                    c.put("line", f.lineRange() != null ? f.lineRange().startLine() : 1);
                    c.put("body", formatFinding(f));
                    return (Map<String, Object>) c;
                })
                .toList();

            if (comments.isEmpty()) return;

            Map<String, Object> body = Map.of(
                "event", "COMMENT",
                "body", "devtown/" + event.capability() + ": " + findings.size() + " finding(s)",
                "comments", comments);

            prApi.createReview(repoParts[0], repoParts[1], pr.prNumber(), body);
            LOG.infof("Posted %d inline comments on %s#%d for %s",
                comments.size(), pr.repo(), pr.prNumber(), event.capability());
        } catch (Exception e) {
            LOG.warnf(e, "Failed to post PR review comments for caseId=%s capability=%s",
                event.caseId(), event.capability());
        }
    }

    @SuppressWarnings("unchecked")
    private List<ReviewFinding> extractFindings(ReviewCompletedEvent event) {
        var caseInstance = caseInstanceRepository.findByUuid(event.caseId()).orElse(null);
        if (caseInstance == null) return List.of();

        var ctx = caseInstance.getCaseContext();
        if (ctx == null) return List.of();

        String contextKey = OutputMappingKeys.contextKeyForCapability(event.capability());
        if (contextKey == null) return List.of();

        Object findingsObj = ctx.getPath(contextKey + ".findings");
        if (!(findingsObj instanceof List<?> list)) return List.of();

        return list.stream()
            .filter(Map.class::isInstance)
            .map(item -> {
                var m = (Map<String, Object>) item;
                ReviewFinding.LineRange range = null;
                if (m.get("startLine") != null) {
                    range = new ReviewFinding.LineRange(
                        ((Number) m.get("startLine")).intValue(),
                        m.get("endLine") != null ? ((Number) m.get("endLine")).intValue()
                            : ((Number) m.get("startLine")).intValue());
                }
                return new ReviewFinding(
                    ReviewFinding.Severity.valueOf((String) m.get("severity")),
                    (String) m.get("category"),
                    (String) m.get("filePath"),
                    range,
                    (String) m.get("message"),
                    m.get("confidence") != null ? ((Number) m.get("confidence")).doubleValue() : 0.0);
            })
            .toList();
    }

    private static String formatFinding(ReviewFinding f) {
        return "**%s** (confidence %.2f): %s".formatted(
            f.severity().name(), f.confidence(), f.message());
    }
}
