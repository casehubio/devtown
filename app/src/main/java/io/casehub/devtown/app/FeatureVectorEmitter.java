package io.casehub.devtown.app;

import io.casehub.devtown.domain.cbr.PrFeatureVector;
import io.casehub.devtown.domain.memory.DevtownMemoryDomain;
import io.casehub.devtown.domain.memory.DevtownMemoryKeys;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryInput;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.UUID;

@ApplicationScoped
public class FeatureVectorEmitter {

    private static final Logger LOG = Logger.getLogger(FeatureVectorEmitter.class);

    @Inject
    Instance<CaseMemoryStore> store;

    public void emit(UUID caseId, String tenantId, PrFeatureVector vector) {
        if (!store.isResolvable()) return;

        try {
            var entityId = DevtownMemoryDomain.CASE_VECTOR_PREFIX + vector.repo() + ":" + caseId;

            var attributes = new HashMap<>(vector.toAttributes());
            attributes.put(DevtownMemoryKeys.ENTITY_TYPE, "case-vector");
            attributes.put(DevtownMemoryKeys.PR_REPO, vector.repo());

            var text = String.format("PR #%d in %s: %d lines, %d modules, %s",
                vector.prNumber(), vector.repo(), vector.linesChanged(),
                vector.modules().size(),
                vector.languages().isEmpty() ? "no languages detected" : String.join(", ", vector.languages()));

            store.get().store(new MemoryInput(
                entityId,
                DevtownMemoryDomain.SOFTWARE_REVIEW,
                tenantId,
                caseId.toString(),
                text,
                attributes
            ));
        } catch (Exception e) {
            LOG.warnf(e, "Feature vector emission failed for case=%s repo=%s — proceeding without stored vector",
                caseId, vector.repo());
        }
    }
}
