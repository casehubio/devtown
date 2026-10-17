package io.casehub.devtown.app.github;

import io.casehub.api.model.TaskStatus;
import io.casehub.devtown.github.GitHubPullRequestApi;
import io.casehub.engine.common.spi.CrossTenantCaseInstanceRepository;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.casehub.engine.common.spi.event.PlanItemStateChangedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class GitHubSummaryCommentObserver {

    private static final Logger LOG = Logger.getLogger(GitHubSummaryCommentObserver.class);

    private static final Set<String> REVIEW_BINDINGS = Set.of(
        "security-review", "architecture-review", "style-check",
        "test-coverage", "performance-analysis", "code-analysis");

    private final Set<UUID> dispatchSummaryPosted = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Set<String>> dispatchedCapabilities = new ConcurrentHashMap<>();

    @Inject @RestClient
    GitHubPullRequestApi prApi;

    @Inject
    CrossTenantCaseInstanceRepository caseInstanceRepository;

    @ConfigProperty(name = "devtown.github.write-back.enabled", defaultValue = "false")
    boolean enabled;

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    void onPlanItemStateChanged(@ObservesAsync PlanItemStateChangedEvent event) {
        if (!REVIEW_BINDINGS.contains(event.bindingName())) return;
        if (event.newStatus() != TaskStatus.RUNNING) return;

        dispatchedCapabilities
            .computeIfAbsent(event.caseId(), k -> ConcurrentHashMap.newKeySet())
            .add(event.bindingName());

        if (!dispatchSummaryPosted.add(event.caseId())) return;

        if (!enabled) {
            LOG.infof("[dev-mode] Would post dispatch summary for caseId=%s", event.caseId());
            return;
        }

        try {
            postComment(event.caseId(), buildDispatchSummary(event.caseId()));
        } catch (Exception e) {
            LOG.warnf(e, "Failed to post dispatch summary for caseId=%s", event.caseId());
            dispatchSummaryPosted.remove(event.caseId());
        }
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        if (!"COMPLETED".equals(event.caseStatus()) && !"CANCELLED".equals(event.caseStatus())) return;

        if (!enabled) {
            LOG.infof("[dev-mode] Would post completion summary for caseId=%s status=%s",
                event.caseId(), event.caseStatus());
            return;
        }

        try {
            String status = "COMPLETED".equals(event.caseStatus())
                ? "All reviews completed successfully."
                : "Review cancelled.";
            postComment(event.caseId(),
                "### devtown review " + event.caseStatus().toLowerCase() + "\n\n" + status);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to post completion summary for caseId=%s", event.caseId());
        } finally {
            dispatchSummaryPosted.remove(event.caseId());
            dispatchedCapabilities.remove(event.caseId());
        }
    }

    private String buildDispatchSummary(UUID caseId) {
        Set<String> capabilities = dispatchedCapabilities.getOrDefault(caseId, Set.of());
        var sb = new StringBuilder("### devtown review dispatched\n\nReviews in progress:\n");
        capabilities.forEach(c -> sb.append("- `").append(c).append("`\n"));
        return sb.toString();
    }

    private void postComment(UUID caseId, String body) {
        var instance = caseInstanceRepository.findByUuid(caseId).orElse(null);
        if (instance == null || instance.getCaseContext() == null) return;

        String repo = instance.getCaseContext().getPathAsString("pr.repo");
        String prIdStr = instance.getCaseContext().getPathAsString("pr.id");
        if (repo == null || prIdStr == null) return;

        String[] repoParts = repo.split("/");
        int prNumber = Integer.parseInt(prIdStr);

        prApi.createIssueComment(repoParts[0], repoParts[1], prNumber,
            Map.of("body", body));
    }
}
