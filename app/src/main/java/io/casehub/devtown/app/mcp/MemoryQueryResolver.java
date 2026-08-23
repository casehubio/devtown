package io.casehub.devtown.app.mcp;

import io.casehub.devtown.domain.memory.DevtownMemoryDomain;
import io.casehub.devtown.domain.memory.ModulePathNormalizer;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryOrder;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.mcp.McpDomain;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

@McpDomain("devtown")
@GraphQLApi
@ApplicationScoped
public class MemoryQueryResolver {

    @Inject Instance<CaseMemoryStore> memoryStoreInstance;
    @Inject CurrentPrincipal principal;
    @Inject io.casehub.devtown.app.trust.EvidentialViolationStore violationStore;
    @Inject io.casehub.devtown.app.CbrWeightOverrideStore cbrWeightOverrides;
    @Inject Instance<io.casehub.devtown.review.CbrRetrievalService> cbrRetrievalService;

    @Query
    @Description("Find prior review decisions for a specific repository and file path, with reasoning traces")
    public List<PriorDecision> priorDecisions(
            @Name("repo") @Description("Repository name") String repo,
            @Name("filePath") @Description("File path within repo") String filePath) {
        String tenant = principal.tenancyId();
        if (!memoryStoreInstance.isResolvable()) return List.of();

        var modules = ModulePathNormalizer.normalize(List.of(filePath));
        List<String> entityIds = modules.stream()
                .map(m -> DevtownMemoryDomain.MODULE_PREFIX + repo + "/" + m)
                .limit(MemoryQuery.MAX_ENTITY_IDS).toList();
        if (entityIds.isEmpty()) return List.of();

        CaseMemoryStore memoryStore = memoryStoreInstance.get();
        var outcomes = memoryStore.query(
                MemoryQuery.forEntities(entityIds, DevtownMemoryDomain.SOFTWARE_REVIEW, tenant)
                        .withLimit(20).withOrder(MemoryOrder.CHRONOLOGICAL));

        Map<String, String> reasoningByCaseId = Map.of();
        List<String> caseIds = outcomes.stream()
                .map(io.casehub.neocortex.memory.Memory::caseId)
                .filter(Objects::nonNull).distinct()
                .limit(MemoryQuery.MAX_ENTITY_IDS).toList();
        if (!caseIds.isEmpty()) {
            List<String> reasoningEntityIds = caseIds.stream()
                    .map(id -> "case:" + id).toList();
            var traces = memoryStore.query(
                    MemoryQuery.forEntities(reasoningEntityIds,
                                    new MemoryDomain("worker-reasoning"), tenant)
                            .withLimit(caseIds.size() * 5)
                            .withOrder(MemoryOrder.CHRONOLOGICAL));
            reasoningByCaseId = traces.stream()
                    .filter(m -> m.caseId() != null)
                    .collect(Collectors.toMap(
                            m -> m.caseId() + ":" + m.attributes().getOrDefault("capability", ""),
                            io.casehub.neocortex.memory.Memory::text, (a, b) -> b));
        }

        Map<String, String> finalReasoningMap = reasoningByCaseId;
        return outcomes.stream()
                .map(m -> {
                    String capability = m.attributes().getOrDefault("capability", "unknown");
                    UUID caseUuid = m.caseId() != null ? UUID.fromString(m.caseId()) : null;
                    int pr = 0;
                    try { pr = Integer.parseInt(m.attributes().getOrDefault("pr-number", "0")); }
                    catch (NumberFormatException ignored) {}
                    String key = m.caseId() != null ? m.caseId() + ":" + capability : null;
                    String reasoning = key != null ? finalReasoningMap.get(key) : null;
                    return new PriorDecision(caseUuid, repo, pr, capability, m.text(),
                            m.createdAt(), reasoning);
                }).toList();
    }

    @Query
    @Description("Search case memory for a contributor's review history — outcomes, patterns, and prior decisions")
    public List<PriorDecision> memoryByContributor(
            @Name("contributor") @Description("Contributor username") String contributor,
            @Name("limit") @Description("Max results") @DefaultValue("20") int limit) {
        if (!memoryStoreInstance.isResolvable()) return List.of();
        var memories = memoryStoreInstance.get().query(
                MemoryQuery.forEntity(
                                DevtownMemoryDomain.CONTRIBUTOR_PREFIX + contributor,
                                DevtownMemoryDomain.SOFTWARE_REVIEW,
                                principal.tenancyId())
                        .withLimit(limit)
                        .withOrder(MemoryOrder.CHRONOLOGICAL));
        return memories.stream()
                .map(m -> new PriorDecision(
                        null, m.attributes().getOrDefault("repo", "unknown"), 0,
                        m.attributes().getOrDefault("capability", "unknown"),
                        m.text(), m.createdAt()))
                .toList();
    }

    @Query
    @Description("Search case memory for all entries related to a specific review capability across contributors")
    public List<PriorDecision> memoryByCapability(
            @Name("capability") @Description("Capability name (e.g. security-review)") String capability,
            @Name("limit") @Description("Max results") @DefaultValue("20") int limit) {
        if (!memoryStoreInstance.isResolvable()) return List.of();
        var memories = memoryStoreInstance.get().scan(
                new io.casehub.neocortex.memory.MemoryScanRequest(
                        principal.tenancyId(),
                        DevtownMemoryDomain.SOFTWARE_REVIEW.name(),
                        "capability", capability, limit, null));
        return memories.stream()
                .map(m -> new PriorDecision(
                        null, m.attributes().getOrDefault("repo", "unknown"), 0,
                        capability, m.text(), m.createdAt()))
                .toList();
    }

    @Query
    @Description("List evidential benchmark violations from FLAGGED attestations — shows which checks failed and why")
    public List<io.casehub.devtown.app.trust.EvidentialViolationStore.ViolationRecord> evidentialViolations(
            @Name("commitmentId") @Description("Optional — filter by commitment UUID") String commitmentId) {
        if (commitmentId != null) {
            return violationStore.get(commitmentId)
                    .map(List::of)
                    .orElse(List.of());
        }
        return violationStore.all();
    }

    @Query
    @Description("Find cases similar to a PR using CBR similarity search — returns ranked precedents with similarity scores")
    public List<io.casehub.devtown.domain.cbr.Precedent> similarCases(
            @Name("repo") @Description("GitHub repo slug") String repo,
            @Name("prNumber") @Description("PR number") int prNumber,
            @Name("contributor") @Description("PR author username") String contributor,
            @Name("linesChanged") @Description("Total lines changed") int linesChanged,
            @Name("changedPaths") @Description("Comma-separated list of changed file paths") String changedPaths) {
        if (!cbrRetrievalService.isResolvable()) return List.of();
        var vector = io.casehub.devtown.domain.cbr.PrFeatureVector.from(
                repo, prNumber, contributor, linesChanged,
                java.util.Arrays.stream(changedPaths.split(",")).map(String::trim).toList());
        return cbrRetrievalService.get().findSimilar(vector, repo, principal.tenancyId());
    }

    @Query
    @Description("Show current CBR similarity weights — base preferences plus any dynamic adjustments from outcome feedback")
    public Map<String, Object> cbrWeightStatus() {
        return Map.of(
                "overrides", cbrWeightOverrides.currentOverrides(),
                "sampleCount", cbrWeightOverrides.sampleCount());
    }

    public record PriorDecision(
            UUID caseId, String repo, int prNumber, String capability,
            String outcome, Instant decidedAt, String reasoning) {
        public PriorDecision(UUID caseId, String repo, int prNumber,
                             String capability, String outcome, Instant decidedAt) {
            this(caseId, repo, prNumber, capability, outcome, decidedAt, null);
        }
    }
}
