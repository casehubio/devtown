package io.casehub.devtown.app.ledger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.casehub.engine.common.spi.event.WorkerDecisionEvent;
import io.casehub.ledger.api.model.LedgerMerkleFrontier;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.spi.LedgerMerkleFrontierRepository;
import io.casehub.ledger.jpa.LedgerPersistenceUnit;
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.devtown.app.trust.TrustScoringTestProfile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(TrustScoringTestProfile.class)
class WorkerDecisionWritePathTest {

    @Inject Event<WorkerDecisionEvent> workerDecisionEvents;
    @Inject LedgerEntryRepository ledgerRepo;
    @Inject LedgerMerkleFrontierRepository frontierRepo;
    @Inject @LedgerPersistenceUnit EntityManager em;

    @Test
    void workerDecision_writesEntryWithTrustEnrichment() {
        UUID caseId = UUID.randomUUID();
        String tenancyId = "test-tenant";
        String workerId = "agent-security-1";
        String capabilityTag = "security-review";

        WorkerDecisionEvent event = new WorkerDecisionEvent(
                caseId, tenancyId, workerId, capabilityTag, "trace-wd-1",
                null, null);

        workerDecisionEvents.fireAsync(event);

        await().atMost(5, SECONDS).untilAsserted(() -> {
            List<WorkerDecisionEntry> entries = findWorkerDecisions(caseId, tenancyId);
            assertThat(entries).hasSize(1);

            WorkerDecisionEntry entry = entries.get(0);
            assertThat(entry.workerId).isEqualTo(workerId);
            assertThat(entry.capabilityTag).isEqualTo(capabilityTag);
            assertThat(entry.caseId).isEqualTo(caseId);
            assertThat(entry.digest).isNotNull();

            List<LedgerMerkleFrontier> frontier = QuarkusTransaction.requiringNew()
                    .call(() -> frontierRepo.findBySubjectId(caseId, tenancyId));
            assertThat(frontier).hasSize(1);
            assertThat(frontier.get(0).hash).isNotNull();
        });
    }

    @Test
    void multipleDecisions_frontierAdvancesCorrectly() {
        UUID caseId = UUID.randomUUID();
        String tenancyId = "test-tenant";

        workerDecisionEvents.fireAsync(new WorkerDecisionEvent(
                caseId, tenancyId, "agent-style-1", "style-review", "trace-wd-2",
                null, null));
        workerDecisionEvents.fireAsync(new WorkerDecisionEvent(
                caseId, tenancyId, "agent-arch-1", "architecture-review", "trace-wd-3",
                null, null));
        workerDecisionEvents.fireAsync(new WorkerDecisionEvent(
                caseId, tenancyId, "agent-perf-1", "performance-analysis", "trace-wd-4",
                null, null));

        await().atMost(5, SECONDS).untilAsserted(() -> {
            List<WorkerDecisionEntry> entries = findWorkerDecisions(caseId, tenancyId);
            assertThat(entries).hasSize(3);

            List<LedgerMerkleFrontier> frontier = QuarkusTransaction.requiringNew()
                    .call(() -> frontierRepo.findBySubjectId(caseId, tenancyId));
            assertThat(frontier).hasSize(Integer.bitCount(3));
            assertThat(frontier).allSatisfy(node ->
                    assertThat(node.hash).isNotNull());
        });
    }

    private List<WorkerDecisionEntry> findWorkerDecisions(UUID caseId, String tenancyId) {
        return QuarkusTransaction.requiringNew().call(() ->
            em.createQuery(
                    "SELECT w FROM WorkerDecisionEntry w WHERE w.caseId = :caseId AND w.tenancyId = :tenancyId",
                    WorkerDecisionEntry.class)
                .setParameter("caseId", caseId)
                .setParameter("tenancyId", tenancyId)
                .getResultList()
        );
    }
}
