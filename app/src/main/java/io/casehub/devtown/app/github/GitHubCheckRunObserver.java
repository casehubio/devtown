package io.casehub.devtown.app.github;

import io.casehub.api.model.TaskStatus;
import io.casehub.devtown.github.GitHubChecksApi;
import io.casehub.engine.common.spi.CrossTenantCaseInstanceRepository;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.casehub.engine.common.spi.event.PlanItemStateChangedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import io.casehub.devtown.review.OutputMappingKeys;
import io.casehub.engine.common.internal.model.CaseInstance;

import java.util.LinkedHashMap;
import java.util.List;
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

    private static final String AGGREGATE_CHECK_NAME = "devtown/review-status";
    private static final Set<String> TERMINAL_CASE_STATUSES = Set.of(
        "COMPLETED", "CANCELLED", "FAULTED", "OBSOLETE");

    private final ConcurrentHashMap<String, Long> checkRunIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> aggregateCheckRunIds = new ConcurrentHashMap<>();

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

        createAggregateCheckRunIfNeeded(event.caseId(), repoParts, headSha);
    }

    private void createAggregateCheckRunIfNeeded(UUID caseId, String[] repoParts, String headSha) {
        if (aggregateCheckRunIds.containsKey(caseId)) return;

        Map<String, Object> body = Map.of(
            "name", AGGREGATE_CHECK_NAME,
            "head_sha", headSha,
            "status", "in_progress");

        var response = checksApi.createCheckRun(repoParts[0], repoParts[1], body);
        if (response != null && response.get("id") != null) {
            long id = ((Number) response.get("id")).longValue();
            aggregateCheckRunIds.put(caseId, id);
            LOG.infof("Created aggregate check run %s (id=%d) for caseId=%s",
                      AGGREGATE_CHECK_NAME, id, caseId);
        }
    }

    void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        if (!TERMINAL_CASE_STATUSES.contains(event.caseStatus())) return;

        Long aggregateId = aggregateCheckRunIds.remove(event.caseId());
        if (aggregateId == null) return;

        if (!enabled) {
            LOG.infof("[dev-mode] Would complete aggregate check run for caseId=%s status=%s",
                event.caseId(), event.caseStatus());
            return;
        }

        try {
            var instance = caseInstanceRepository.findByUuid(event.caseId()).orElse(null);
            if (instance == null || instance.getCaseContext() == null) return;
            String repo = instance.getCaseContext().getPathAsString("pr.repo");
            if (repo == null) return;
            String[] repoParts = repo.split("/");

            String conclusion = "COMPLETED".equals(event.caseStatus()) ? "success" : "failure";

            var body = new LinkedHashMap<String, Object>();
            body.put("status", "completed");
            body.put("conclusion", conclusion);
            body.put("output", Map.of(
                "title", AGGREGATE_CHECK_NAME,
                "summary", "COMPLETED".equals(event.caseStatus())
                    ? "All devtown reviews completed."
                    : "Review " + event.caseStatus().toLowerCase() + "."));

            checksApi.updateCheckRun(repoParts[0], repoParts[1], aggregateId, body);
            LOG.infof("Updated aggregate check run (id=%d) conclusion=%s for caseId=%s",
                      aggregateId, conclusion, event.caseId());
        } catch (Exception e) {
            LOG.warnf(e, "Failed to update aggregate check run for caseId=%s", event.caseId());
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

        String conclusion;
        if (event.newStatus() != TaskStatus.COMPLETED) {
            conclusion = "failure";
        } else if (hasHighOrCriticalFindings(instance, event.bindingName())) {
            conclusion = "action_required";
        } else {
            conclusion = "success";
        }

        var body = new LinkedHashMap<String, Object>();
        body.put("status", "completed");
        body.put("conclusion", conclusion);
        body.put("output", Map.of(
                "title", "devtown/" + event.bindingName(),
                "summary", switch (conclusion) {
                    case "success"         -> "Review completed — no blocking findings.";
                    case "action_required" -> "Review completed — HIGH/CRITICAL findings require attention.";
                    default                -> "Review " + event.newStatus().name().toLowerCase() + ".";
                }));

        checksApi.updateCheckRun(repoParts[0], repoParts[1], checkRunId, body);
        LOG.infof("Updated check run devtown/%s (id=%d) conclusion=%s",
                  event.bindingName(), checkRunId, conclusion);
    }

    @SuppressWarnings("unchecked")
    private boolean hasHighOrCriticalFindings(CaseInstance instance, String bindingName) {
        var ctx = instance.getCaseContext();
        if (ctx == null) return false;

        String contextKey = OutputMappingKeys.contextKeyForCapability(bindingName);
        if (contextKey == null) return false;

        Object findingsObj = ctx.getPath(contextKey + ".findings");
        if (!(findingsObj instanceof List<?> list)) return false;

        return list.stream()
            .filter(Map.class::isInstance)
            .map(item -> (Map<String, Object>) item)
            .anyMatch(m -> {
                String sev = (String) m.get("severity");
                return "HIGH".equals(sev) || "CRITICAL".equals(sev);
            });
    }

    private static String checkRunKey(UUID caseId, String bindingName) {
        return caseId + ":" + bindingName;
    }
}
