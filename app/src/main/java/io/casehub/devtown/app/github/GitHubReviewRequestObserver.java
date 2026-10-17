package io.casehub.devtown.app.github;

import io.casehub.devtown.github.GitHubPullRequestApi;
import io.casehub.engine.common.spi.CrossTenantCaseInstanceRepository;
import io.casehub.work.api.WorkItemLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class GitHubReviewRequestObserver {

    private static final Logger LOG = Logger.getLogger(GitHubReviewRequestObserver.class);

    @Inject @RestClient
    GitHubPullRequestApi prApi;

    @Inject
    CrossTenantCaseInstanceRepository caseInstanceRepository;

    @ConfigProperty(name = "devtown.github.write-back.enabled", defaultValue = "false")
    boolean enabled;

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    void onWorkItemCreated(
            @Observes(during = TransactionPhase.AFTER_SUCCESS)
            WorkItemLifecycleEvent event) {
        if (!event.type().endsWith(".created")) return;
        if (event.types() == null || !event.types().contains("human-decision:pr-approval")) return;

        if (!enabled) {
            LOG.infof("[dev-mode] Would request reviewers for callerRef=%s assignee=%s",
                event.callerRef(), event.assigneeId());
            return;
        }

        try {
            UUID caseId = UUID.fromString(event.callerRef());
            var instance = caseInstanceRepository.findByUuid(caseId).orElse(null);
            if (instance == null || instance.getCaseContext() == null) return;

            String repo = instance.getCaseContext().getPathAsString("pr.repo");
            String prIdStr = instance.getCaseContext().getPathAsString("pr.id");
            if (repo == null || prIdStr == null) return;

            String[] repoParts = repo.split("/");
            int prNumber = Integer.parseInt(prIdStr);

            Map<String, Object> body = Map.of(
                "reviewers", List.of(event.assigneeId()));

            prApi.requestReviewers(repoParts[0], repoParts[1], prNumber, body);
            LOG.infof("Requested reviewer %s on %s#%d", event.assigneeId(), repo, prNumber);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to request reviewers for callerRef=%s", event.callerRef());
        }
    }
}
