package io.casehub.devtown.app.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.devtown.domain.DeterministicUuid;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.model.CaseLedgerEntry;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.api.model.supplement.ComplianceSupplement;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Observes terminal {@link CaseLifecycleEvent} transitions and writes a
 * {@link MergeDecisionLedgerEntry} to the tamper-evident audit trail.
 *
 * <p>Handles two distinct paths, distinguished by case context shape:
 *
 * <p><strong>PR review path</strong> (existing): Case context has {@code pr.repo},
 * {@code pr.id}. Batch columns stay null. Single entry per case.
 *
 * <p><strong>Merge batch path</strong> (new): Case context has {@code batch.*}
 * AND {@code batch.isRootBatch == true}. The observer skips sub-case completions
 * during bisection — sub-cases receive {@code { batch: .splitResult.left }} from
 * the splitter, which does not include {@code isRootBatch}. Only root batch cases
 * set {@code batch.isRootBatch = true} at dispatch. Without this guard, bisection
 * sub-case completions write duplicate ledger entries. For root batches: iterates
 * {@code batch.prs}, writes one entry per PR with shared batch metadata.
 *
 * <p>Decision semantics:
 * <ul>
 *   <li>{@code COMPLETED} → {@code APPROVED} — all goals met
 *   <li>{@code CANCELLED} → {@code REJECTED} — case explicitly aborted
 *   <li>{@code FAULTED} → no entry — infrastructure error, not a merge decision
 * </ul>
 */
@ApplicationScoped
public class MergeDecisionObserver {

    private static final Logger LOG = Logger.getLogger(MergeDecisionObserver.class);

    @Inject LedgerEntryRepository ledgerRepo;
    @Inject LedgerConfig ledgerConfig;
    @Inject ObjectMapper objectMapper;

    void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        if (!ledgerConfig.enabled()) return;

        String decision = switch (event.caseStatus()) {
            case "COMPLETED" -> "APPROVED";
            case "CANCELLED" -> "REJECTED";
            default -> null;
        };
        if (decision == null) return;

        JsonNode snapshot = event.contextSnapshot();
        if (snapshot == null) return;

        String prRepo = snapshot.path("pr").path("repo").asText(null);
        String batchIdStr = snapshot.path("batch").path("id").asText(null);

        try {
            QuarkusTransaction.requiringNew().run(() -> {
                if (prRepo != null) {
                    handlePrReviewPath(event, snapshot, decision);
                } else if (batchIdStr != null) {
                    handleMergeBatchPath(event, snapshot, decision);
                }
            });
        } catch (Exception e) {
            LOG.errorf(e, "Failed to write merge decision for caseId=%s decision=%s",
                    event.caseId(), decision);
        }
    }

    private void handlePrReviewPath(CaseLifecycleEvent event, JsonNode snapshot, String decision) {
        String repo = snapshot.path("pr").path("repo").asText(null);
        String prIdStr = snapshot.path("pr").path("id").asText(null);
        String headSha = snapshot.path("pr").path("headSha").asText(null);
        String mergeSha = snapshot.path("merge_sha").asText(null);
        if (repo == null || prIdStr == null) return;

        int prNumber;
        try {
            prNumber = Integer.parseInt(prIdStr);
        } catch (NumberFormatException e) {
            return;
        }

        MergeDecisionLedgerEntry entry = new MergeDecisionLedgerEntry();
        entry.subjectId = event.caseId();
        entry.caseId = event.caseId();
        entry.tenancyId = event.tenancyId();
        entry.entryType = LedgerEntryType.EVENT;
        entry.prNumber = prNumber;
        entry.repository = repo;
        entry.commitSha = mergeSha != null ? mergeSha : headSha;
        entry.decision = decision;
        entry.actorId = "system";
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "ORCHESTRATOR";
        entry.occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        ledgerRepo.findLatestBySubjectId(event.caseId(), event.tenancyId())
                .filter(latest -> latest instanceof CaseLedgerEntry cle
                        && event.caseStatus().equals(cle.caseStatus))
                .ifPresent(latest -> entry.causedByEntryId = latest.id);

        ComplianceSupplement cs = new DevtownComplianceSupplement();
        cs.algorithmRef = "casehub-devtown:pr-review-v1";
        cs.humanOverrideAvailable = true;
        cs.contestationUri = "/api/reviews/" + prNumber + "/contest";
        entry.attach(cs);

        ledgerRepo.save(entry, event.tenancyId());
        LOG.debugf("Merge decision written (PR review path): caseId=%s decision=%s pr=%s#%d",
                event.caseId(), decision, repo, prNumber);
    }

    private void handleMergeBatchPath(CaseLifecycleEvent event, JsonNode snapshot, String decision) {
        JsonNode batch = snapshot.path("batch");

        if (!batch.path("isRootBatch").asBoolean(false)) {
            LOG.debugf("Skipping batch sub-case completion: caseId=%s (not a root batch)", event.caseId());
            return;
        }

        String batchId = batch.path("id").asText(null);
        String repository = batch.path("repository").asText(null);
        Integer batchSize = batch.has("size") && batch.path("size").isNumber()
                ? batch.path("size").intValue() : null;
        Boolean bisectionOccurred = batch.has("bisectionOccurred") && batch.path("bisectionOccurred").isBoolean()
                ? batch.path("bisectionOccurred").asBoolean() : null;
        String bisectionStrategy = batch.path("bisectionStrategy").asText(null);

        String batchContextJson = serializeBatchContext(snapshot);

        JsonNode prsNode = batch.path("prs");
        if (!prsNode.isArray()) {
            LOG.warnf("batch.prs is not an array for caseId=%s", event.caseId());
            return;
        }

        int entriesWritten = 0;
        for (JsonNode prItem : prsNode) {
            if (!prItem.isObject()) continue;

            JsonNode prNumberNode = prItem.path("number");
            if (!prNumberNode.isNumber()) continue;
            int prNumber = prNumberNode.intValue();

            UUID subjectId = DeterministicUuid.v5(
                    DeterministicUuid.MERGE_DECISION_NS,
                    event.caseId() + ":" + prNumber);

            MergeDecisionLedgerEntry entry = new MergeDecisionLedgerEntry();
            entry.subjectId = subjectId;
            entry.caseId = event.caseId();
            entry.tenancyId = event.tenancyId();
            entry.entryType = LedgerEntryType.EVENT;
            entry.prNumber = prNumber;
            entry.repository = repository;
            entry.commitSha = null;
            entry.decision = decision;
            entry.actorId = "system";
            entry.actorType = ActorType.SYSTEM;
            entry.actorRole = "ORCHESTRATOR";
            entry.occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

            entry.batchId = batchId;
            entry.batchSize = batchSize;
            entry.bisectionOccurred = bisectionOccurred;
            entry.bisectionStrategy = bisectionStrategy;
            entry.batchContextJson = batchContextJson;

            ledgerRepo.findLatestBySubjectId(event.caseId(), event.tenancyId())
                    .filter(latest -> latest instanceof CaseLedgerEntry cle
                            && event.caseStatus().equals(cle.caseStatus))
                    .ifPresent(latest -> entry.causedByEntryId = latest.id);

            ComplianceSupplement cs = new DevtownComplianceSupplement();
            cs.algorithmRef = "casehub-devtown:merge-queue-v1";
            cs.humanOverrideAvailable = true;
            cs.contestationUri = "/api/merge-queue/batches/" + batchId + "/contest";
            entry.attach(cs);

            ledgerRepo.save(entry, event.tenancyId());
            entriesWritten++;
        }

        LOG.debugf("Merge decision written (batch path): caseId=%s decision=%s batchId=%s entries=%d",
                event.caseId(), decision, batchId, entriesWritten);
    }

    private String serializeBatchContext(JsonNode snapshot) {
        JsonNode batch = snapshot.path("batch");
        Map<String, Object> batchContext = new LinkedHashMap<>();

        JsonNode prsNode = batch.path("prs");
        if (prsNode.isArray()) {
            batchContext.put("prList", prsNode);
        }

        JsonNode trustScoresNode = batch.path("trustScoresAtDecision");
        if (trustScoresNode.isObject()) {
            batchContext.put("trustScoresAtDecision", trustScoresNode);
        }

        JsonNode ciRunIdsNode = batch.path("ciRunIds");
        if (ciRunIdsNode.isArray()) {
            batchContext.put("ciRunIds", ciRunIdsNode);
        }

        JsonNode rejectedPrsNode = batch.path("rejectedPrs");
        if (rejectedPrsNode.isArray()) {
            batchContext.put("rejectedPrs", rejectedPrsNode);
        }

        try {
            return objectMapper.writeValueAsString(batchContext);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to serialize batch context to JSON — returning empty object");
            return "{}";
        }
    }
}
