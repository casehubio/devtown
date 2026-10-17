package io.casehub.devtown.app.github;

import io.casehub.api.model.TaskStatus;
import io.casehub.devtown.github.GitHubChecksApi;
import io.casehub.engine.common.spi.CrossTenantCaseInstanceRepository;
import io.casehub.engine.common.spi.event.PlanItemStateChangedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class GitHubCheckRunObserver {

    private static final Logger LOG = Logger.getLogger(GitHubCheckRunObserver.class);

    private static final Set<String> CHECK_RUN_BINDINGS = Set.of(
        "security-review", "architecture-review", "style-check",
        "test-coverage", "performance-analysis", "code-analysis");

    private final ConcurrentHashMap<String, Long> checkRunIds = new ConcurrentHashMap<>();

    @Inject @RestClient
    GitHubChecksApi checksApi;

    @Inject
    CrossTenantCaseInstanceRepository caseInstanceRepository;

    @ConfigProperty(name = "devtown.github.write-back.enabled", defaultValue = "false")
    boolean enabled;

    void onPlanItemStateChanged(@ObservesAsync PlanItemStateChangedEvent event) {
        if (!CHECK_RUN_BINDINGS.contains(event.bindingName())) return;
        if (!enabled) {
            LOG.infof("[dev-mode] Would update check run devtown/%s status=%s",
                event.bindingName(), event.newStatus());
            return;
        }

        try {
            if (event.newStatus() == TaskStatus.RUNNING) {
                handleStarted(event);
            } else if (event.newStatus().isTerminal()) {
                handleTerminal(event);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to update check run for caseId=%s binding=%s",
                event.caseId(), event.bindingName());
        }
    }

    private void handleStarted(PlanItemStateChangedEvent event) {
        var instance = caseInstanceRepository.findByUuid(event.caseId()).orElse(null);
        if (instance == null || instance.getCaseContext() == null) {return;}

        String repo    = instance.getCaseContext().getPathAsString("pr.repo");
        String headSha = instance.getCaseContext().getPathAsString("pr.headSha");
        if (repo == null || headSha == null) {return;}

        String[] repoParts = repo.split("/");
        String   checkName = "devtown/" + event.bindingName();
        Map<String, Object> body = Map.of(
                "name", checkName,
                "head_sha", headSha,
                "status", "in_progress");

        var response = checksApi.createCheckRun(repoParts[0], repoParts[1], body);
        if (response != null && response.get("id") != null) {
            long checkRunId = ((Number) response.get("id")).longValue();
            checkRunIds.put(checkRunKey(event.caseId(), event.bindingName()), checkRunId);
            LOG.infof("Created check run %s (id=%d) for caseId=%s",
                      checkName, checkRunId, event.caseId());
        }
    }

    private void handleTerminal(PlanItemStateChangedEvent event) {
        String key        = checkRunKey(event.caseId(), event.bindingName());
        Long   checkRunId = checkRunIds.remove(key);
        if (checkRunId == null) {
            LOG.warnf("No check run ID found for %s — cannot update", key);
            return;
        }

        var instance = caseInstanceRepository.findByUuid(event.caseId()).orElse(null);
        if (instance == null || instance.getCaseContext() == null) {return;}
        String repo = instance.getCaseContext().getPathAsString("pr.repo");
        if (repo == null) {return;}
        String[] repoParts = repo.split("/");

        String conclusion = event.newStatus() == TaskStatus.COMPLETED ? "success" : "failure";

        var body = new LinkedHashMap<String, Object>();
        body.put("status", "completed");
        body.put("conclusion", conclusion);
        body.put("output", Map.of(
                "title", "devtown/" + event.bindingName(),
                "summary", conclusion.equals("success")
                           ? "Review completed — no blocking findings."
                           : "Review " + event.newStatus().name().toLowerCase() + "."));

        checksApi.updateCheckRun(repoParts[0], repoParts[1], checkRunId, body);
        LOG.infof("Updated check run devtown/%s (id=%d) conclusion=%s",
                  event.bindingName(), checkRunId, conclusion);
    }

    private static String checkRunKey(UUID caseId, String bindingName) {
        return caseId + ":" + bindingName;
    }
}
