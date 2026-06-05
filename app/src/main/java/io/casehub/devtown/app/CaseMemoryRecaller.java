package io.casehub.devtown.app;

import io.casehub.devtown.domain.memory.DevtownMemoryDomain;
import io.casehub.devtown.domain.memory.ModulePathNormalizer;
import io.casehub.devtown.review.MemoryContext;
import io.casehub.devtown.review.PrPayload;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.memory.CaseMemoryStore;
import io.casehub.platform.api.memory.Memory;
import io.casehub.platform.api.memory.MemoryOrder;
import io.casehub.platform.api.memory.MemoryQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Recalls memory context before case open.
 * <p>
 * Runs in request scope — {@link CurrentPrincipal} is available.
 * Queries contributor and code area history from {@link CaseMemoryStore}.
 * <p>
 * Fail-open: query failures return {@link MemoryContext#EMPTY}.
 */
@ApplicationScoped
public class CaseMemoryRecaller {

    private static final Logger LOG = Logger.getLogger(CaseMemoryRecaller.class);

    @Inject
    Instance<CaseMemoryStore> store;

    @Inject
    CurrentPrincipal principal;

    public MemoryContext recall(PrPayload pr) {
        if (!store.isResolvable()) {
            return MemoryContext.EMPTY;
        }

        var s = store.get();
        try {
            String tenantId = principal.tenancyId();
            Instant since = Instant.now().minus(Duration.ofDays(90));

            List<Memory> contributorHistory = s.query(
                MemoryQuery.forEntity(
                    "contributor:" + pr.contributor(),
                    DevtownMemoryDomain.SOFTWARE_REVIEW,
                    tenantId)
                .withLimit(10)
                .withSince(since)
                .withOrder(MemoryOrder.CHRONOLOGICAL)
            );

            var modules = ModulePathNormalizer.normalize(pr.changedPaths());
            List<String> moduleIds = modules.stream()
                .map(m -> "module:" + pr.repo() + "/" + m)
                .limit(MemoryQuery.MAX_ENTITY_IDS)
                .toList();

            List<Memory> codeAreaHistory = moduleIds.isEmpty()
                ? List.of()
                : s.query(
                    MemoryQuery.forEntities(
                        moduleIds,
                        DevtownMemoryDomain.SOFTWARE_REVIEW,
                        tenantId)
                    .withLimit(15)
                    .withSince(since)
                    .withOrder(MemoryOrder.RELEVANCE)
                    .withQuestion("review history for "
                        + String.join(", ", modules)
                        + " in " + pr.repo())
                );

            return new MemoryContext(contributorHistory, codeAreaHistory);
        } catch (Exception e) {
            LOG.warnf(e, "Memory recall failed for contributor=%s — proceeding without memory",
                pr.contributor());
            return MemoryContext.EMPTY;
        } finally {
            store.destroy(s);
        }
    }
}
