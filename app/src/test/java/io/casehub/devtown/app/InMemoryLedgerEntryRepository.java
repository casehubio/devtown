package io.casehub.devtown.app;

import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.ReactiveLedgerEntryRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory stub for {@link ReactiveLedgerEntryRepository} — satisfies CDI
 * resolution in @QuarkusTest without a real database.
 *
 * <p>LAYER 1 GAP (tutorial note): no persistence, no Merkle chain, no privacy
 * suppression. A production deployment wires the real Panache implementation.</p>
 */
@ApplicationScoped
class InMemoryLedgerEntryRepository implements ReactiveLedgerEntryRepository {

    private final List<LedgerEntry> entries = new CopyOnWriteArrayList<>();
    private final List<LedgerAttestation> attestations = new CopyOnWriteArrayList<>();

    @Override
    public Uni<LedgerEntry> save(LedgerEntry entry, String tenancyId) {
        entries.add(entry);
        return Uni.createFrom().item(entry);
    }

    @Override
    public Uni<List<LedgerEntry>> findBySubjectId(UUID subjectId, String tenancyId) {
        return Uni.createFrom().item(
            entries.stream().filter(e -> subjectId.equals(e.subjectId)).toList());
    }

    // Stub returns — these methods are not exercised in Layer 1 tests.
    // Implement against the in-memory list if a test exercises these paths.

    @Override
    public Uni<List<LedgerEntry>> findBySubjectIdAndTimeRange(UUID subjectId, Instant from, Instant to, String tenancyId) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<Optional<LedgerEntry>> findLatestBySubjectId(UUID subjectId, String tenancyId) {
        return Uni.createFrom().item(
            entries.stream()
                .filter(e -> subjectId.equals(e.subjectId))
                .max(java.util.Comparator.comparing(e -> e.occurredAt)));
    }

    @Override
    public Uni<Optional<LedgerEntry>> findEntryById(UUID id, String tenancyId) {
        return Uni.createFrom().item(
            entries.stream().filter(e -> id.equals(e.id)).findFirst());
    }

    @Override
    public Uni<List<LedgerEntry>> findByActorId(String actorId, Instant from, Instant to, String tenancyId) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<List<LedgerEntry>> findByActorRole(String role, Instant from, Instant to, String tenancyId) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<List<LedgerEntry>> findCausedBy(UUID causeId, String tenancyId) {
        return Uni.createFrom().item(List.of());
    }

    // findByTimeRange removed from interface

    @Override
    public Uni<LedgerAttestation> saveAttestation(LedgerAttestation attestation, String tenancyId) {
        attestations.add(attestation);
        return Uni.createFrom().item(attestation);
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByEntryId(UUID entryId, String tenancyId) {
        return Uni.createFrom().item(List.of());
    }

    // findAttestationsForEntries removed from interface

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByEntryIdAndCapabilityTag(UUID entryId, String tag, String tenancyId) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByEntryIdGlobal(UUID entryId, String tenancyId) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByAttestorIdAndCapabilityTag(String attestorId, String tag, String tenancyId) {
        return Uni.createFrom().item(List.of());
    }
}
