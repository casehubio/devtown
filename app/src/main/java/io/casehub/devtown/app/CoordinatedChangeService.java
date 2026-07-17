package io.casehub.devtown.app;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.devtown.domain.CoordinatedChangeRequest;
import io.casehub.devtown.domain.RepoChangeEntry;
import io.casehub.devtown.review.CoordinatedChangeOutcome;
import io.casehub.devtown.review.CoordinatedChangePort;
import io.casehub.devtown.review.PrPayload;
import io.casehub.devtown.review.PrReviewApplicationService;
import io.casehub.devtown.app.mcp.PrReviewCaseTracker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class CoordinatedChangeService implements CoordinatedChangePort {

    private static final Logger LOG = Logger.getLogger(CoordinatedChangeService.class);

    @Inject CoordinatedChangeCaseHub caseHub;
    @Inject PrReviewApplicationService reviewService;
    @Inject CoordinatedChangeTracker tracker;
    @Inject PrReviewCaseTracker prReviewCaseTracker;
    @Inject CaseHubRuntime caseHubRuntime;

    @Override
    public CoordinatedChangeOutcome start(CoordinatedChangeRequest request) {
        for (RepoChangeEntry entry : request.repos()) {
            String fullRepo = entry.owner() + "/" + entry.repo();
            var active = prReviewCaseTracker.findActiveCaseByPr(fullRepo, entry.prNumber());
            if (active.isPresent()) {
                throw new IllegalStateException(
                    "Active review exists for " + fullRepo + "#" + entry.prNumber());
            }
        }

        var reposContext = request.repos().stream().map(e -> {
            var m = new LinkedHashMap<String, Object>();
            m.put("owner", e.owner());
            m.put("repo", e.repo());
            m.put("prNumber", e.prNumber());
            m.put("headSha", e.headSha());
            m.put("targetBranch", e.targetBranch());
            return m;
        }).toList();

        UUID parentCaseId = caseHub.startCase(Map.of("repos", reposContext))
            .toCompletableFuture().join();

        Map<String, UUID> started = new LinkedHashMap<>();
        try {
            for (RepoChangeEntry entry : request.repos()) {
                String fullRepo = entry.owner() + "/" + entry.repo();
                var pr = new PrPayload(fullRepo, entry.prNumber(), entry.headSha(),
                    entry.targetBranch(), entry.linesChanged(), entry.contributor(),
                    entry.changedPaths());
                var outcome = reviewService.startReview(pr, Map.of("coordinatedChange", true));
                tracker.register(parentCaseId, fullRepo, outcome.caseId());
                started.put(fullRepo, outcome.caseId());
            }
        } catch (Exception e) {
            LOG.errorf(e, "Partial failure starting coordinated change — cleaning up %d started reviews", started.size());
            started.values().forEach(id -> {
                try { caseHubRuntime.cancelCase(id); } catch (Exception ex) { LOG.warnf(ex, "Cleanup cancel failed for %s", id); }
            });
            try { caseHubRuntime.cancelCase(parentCaseId); } catch (Exception ex) { LOG.warnf(ex, "Cleanup cancel failed for parent %s", parentCaseId); }
            throw new RuntimeException("Coordinated change start failed after " + started.size() + " reviews", e);
        }

        caseHubRuntime.signal(parentCaseId, "reviewCases", started)
            .toCompletableFuture().join();

        return new CoordinatedChangeOutcome(parentCaseId, started);
    }
}
