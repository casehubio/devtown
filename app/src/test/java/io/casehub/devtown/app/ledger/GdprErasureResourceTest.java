package io.casehub.devtown.app.ledger;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.model.LedgerMerkleFrontier;
import io.casehub.ledger.model.CaseLedgerEntry;
import io.casehub.ledger.api.spi.ActorIdentityProvider;
import io.casehub.ledger.jpa.ErasureReceiptLedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.spi.LedgerMerkleFrontierRepository;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(TokenisationEnabledTestProfile.class)
@TestSecurity(user = "devtown-admin", roles = {"devtown-admin"})
class GdprErasureResourceTest {

    @Inject LedgerEntryRepository ledgerRepo;
    @Inject ActorIdentityProvider actorIdentityProvider;
    @Inject CurrentPrincipal principal;
    @Inject Event<CaseLifecycleEvent> caseLifecycleEvents;
    @Inject LedgerMerkleFrontierRepository frontierRepo;
    @Inject ObjectMapper objectMapper;

    @Test
    void erasure_pseudonymisesActorAndReturnsReceipt() {
        final String rawActorId = "human-reviewer-gdpr-" + UUID.randomUUID();
        final UUID caseId = UUID.randomUUID();
        final String tenancyId = principal.tenancyId();

        seedHumanEntry(rawActorId, caseId, tenancyId);

        String token = QuarkusTransaction.requiringNew().call(
                () -> actorIdentityProvider.tokeniseForQuery(rawActorId).orElse(rawActorId));
        assertThat(token).isNotEqualTo(rawActorId);

        var response = given()
            .queryParam("reason", "GDPR Art.17 request")
            .when()
                .post("/api/devtown/compliance/erasure/" + rawActorId)
            .then()
                .statusCode(200)
                .extract().body().jsonPath();

        assertThat(response.getString("erasedActorToken")).isEqualTo(token);
        assertThat(response.getLong("ledgerEntriesAffected")).isGreaterThan(0);
        assertThat(response.getString("receiptEntryId")).isNotNull();
        assertThat(response.getString("reason")).isEqualTo("GDPR Art.17 request");

        var resolved = QuarkusTransaction.requiringNew().call(
                () -> actorIdentityProvider.resolve(token));
        assertThat(resolved).isNotPresent();
    }

    @Test
    void erasure_receiptPersistedByFoundationLedger() {
        final String rawActorId = "human-reviewer-receipt-" + UUID.randomUUID();
        final UUID caseId = UUID.randomUUID();
        final String tenancyId = principal.tenancyId();

        seedHumanEntry(rawActorId, caseId, tenancyId);

        var response = given()
            .queryParam("reason", "GDPR Art.17 test")
            .when()
                .post("/api/devtown/compliance/erasure/" + rawActorId)
            .then()
                .statusCode(200)
                .extract().body().jsonPath();

        UUID receiptId = UUID.fromString(response.getString("receiptEntryId"));

        var receipt = QuarkusTransaction.requiringNew().call(() ->
                ledgerRepo.findEntryById(receiptId, tenancyId));
        assertThat(receipt).isPresent();
        assertThat(receipt.get()).isInstanceOf(ErasureReceiptLedgerEntry.class);

        var erasureReceipt = (ErasureReceiptLedgerEntry) receipt.get();
        assertThat(erasureReceipt.erasedActorId).isEqualTo(rawActorId);
        assertThat(erasureReceipt.actorType).isEqualTo(ActorType.SYSTEM);
        assertThat(erasureReceipt.actorRole).isEqualTo("ErasureService");
    }

    @Test
    void erasure_idempotent_secondCallReturnsZeroCounts() {
        final String rawActorId = "human-reviewer-idempotent-" + UUID.randomUUID();
        final UUID caseId = UUID.randomUUID();
        final String tenancyId = principal.tenancyId();

        seedHumanEntry(rawActorId, caseId, tenancyId);

        given()
            .queryParam("reason", "first erasure")
            .when()
                .post("/api/devtown/compliance/erasure/" + rawActorId)
            .then()
                .statusCode(200);

        var response = given()
            .queryParam("reason", "second erasure")
            .when()
                .post("/api/devtown/compliance/erasure/" + rawActorId)
            .then()
                .statusCode(200)
                .extract().body().jsonPath();

        assertThat(response.getLong("ledgerEntriesAffected")).isEqualTo(0);
    }

    @Test
    void erasure_unknownActor_returns200WithZeroCounts() {
        final String tenancyId = principal.tenancyId();

        var response = given()
            .queryParam("reason", "GDPR Art.17 request")
            .when()
                .post("/api/devtown/compliance/erasure/nonexistent-actor-" + UUID.randomUUID())
            .then()
                .statusCode(200)
                .extract().body().jsonPath();

        assertThat(response.getLong("ledgerEntriesAffected")).isEqualTo(0);
        assertThat(response.getInt("memoryRecordsErased")).isEqualTo(0);
        assertThat(response.getString("receiptEntryId")).isNotNull();
    }

    @Test
    void erasure_preservesMerkleFrontierIntegrity() {
        final UUID caseId = UUID.randomUUID();
        final String tenancyId = principal.tenancyId();
        final String rawActorId = "human-reviewer-frontier-" + UUID.randomUUID();

        ObjectNode snapshot = objectMapper.createObjectNode();
        ObjectNode pr = snapshot.putObject("pr");
        pr.put("repo", "casehubio/devtown");
        pr.put("id", "77");
        pr.put("headSha", "fff999");
        pr.put("baseRef", "main");
        pr.put("linesChanged", 50);
        pr.put("contributor", rawActorId);
        pr.putArray("changedPaths").add("src/Foo.java");

        CaseLifecycleEvent event = new CaseLifecycleEvent(
                caseId, tenancyId, "COMPLETE", "CASE_COMPLETED",
                "COMPLETED", rawActorId, "REVIEWER", "trace-erasure-frontier",
                null, null, snapshot, null, null);

        caseLifecycleEvents.fireAsync(event);

        await().atMost(5, SECONDS).untilAsserted(() -> {
            List<LedgerMerkleFrontier> frontier = QuarkusTransaction.requiringNew()
                    .call(() -> frontierRepo.findBySubjectId(caseId, tenancyId));
            assertThat(frontier).isNotEmpty();
        });

        var response = given()
            .queryParam("reason", "GDPR Art.17 frontier test")
            .when()
                .post("/api/devtown/compliance/erasure/" + rawActorId)
            .then()
                .statusCode(200)
                .extract().body().jsonPath();

        assertThat(response.getLong("ledgerEntriesAffected")).isGreaterThan(0);

        List<LedgerMerkleFrontier> frontierAfter = QuarkusTransaction.requiringNew()
                .call(() -> frontierRepo.findBySubjectId(caseId, tenancyId));
        assertThat(frontierAfter).isNotEmpty();
        assertThat(frontierAfter).allSatisfy(node ->
                assertThat(node.hash).isNotNull());

        UUID receiptId = UUID.fromString(response.getString("receiptEntryId"));
        var receipt = QuarkusTransaction.requiringNew().call(() ->
                ledgerRepo.findEntryById(receiptId, tenancyId));
        assertThat(receipt).isPresent();
        assertThat(receipt.get().digest).isNotNull();
    }

    private void seedHumanEntry(final String actorId, final UUID caseId, final String tenancyId) {
        QuarkusTransaction.requiringNew().run(() -> {
            CaseLedgerEntry entry = new CaseLedgerEntry();
            entry.subjectId = caseId;
            entry.caseId = caseId;
            entry.entryType = LedgerEntryType.EVENT;
            entry.eventType = "REVIEW_COMPLETED";
            entry.caseStatus = "IN_PROGRESS";
            entry.actorId = actorId;
            entry.actorType = ActorType.HUMAN;
            entry.actorRole = "REVIEWER";
            entry.occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            ledgerRepo.save(entry, tenancyId);
        });
    }
}
