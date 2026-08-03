package io.casehub.devtown.app.ledger;

import io.casehub.blocks.attestation.AttestationIntent;
import io.casehub.blocks.attestation.AttestationIntentWriter;
import io.casehub.devtown.app.trust.ContributorAttestationPolicy;
import io.casehub.devtown.review.PrLifecycleEvent;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ContributorOutcomeLedgerWriter {

    private static final Logger LOG = Logger.getLogger(ContributorOutcomeLedgerWriter.class);

    @Inject LedgerEntryRepository ledgerRepo;
    @Inject AttestationIntentWriter intentWriter;
    @Inject CurrentPrincipal principal;
    @Inject TransactionManager tm;

    public UUID writeOutcome(PrLifecycleEvent event, String outcome,
                             Integer reviewRounds, List<AttestationIntent> intents) {
        return writeOutcome(event, outcome, reviewRounds, intents,
                Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    public UUID writeOutcome(PrLifecycleEvent event, String outcome,
                             Integer reviewRounds, List<AttestationIntent> intents,
                             Instant occurredAt) {
        assertNoActiveTransaction();
        UUID entryId = ContributorAttestationPolicy.deterministicEntryId(
                event.repo(), event.prNumber(), outcome);
        String tenancyId = principal.tenancyId();

        if (ledgerRepo.findEntryById(entryId, tenancyId).isEmpty()) {
            ContributorOutcomeLedgerEntry entry = new ContributorOutcomeLedgerEntry();
            entry.id = entryId;
            entry.subjectId = ContributorAttestationPolicy.resolveSubjectId(event);
            entry.actorId = event.contributorId();
            entry.actorType = ActorType.HUMAN;
            entry.actorRole = "contributor";
            entry.entryType = LedgerEntryType.EVENT;
            entry.occurredAt = occurredAt;
            entry.prNumber = event.prNumber();
            entry.repository = event.repo();
            entry.outcome = outcome;
            entry.reviewRounds = reviewRounds;
            entry.caseId = extractCaseId(event);

            try {
                ledgerRepo.save(entry, tenancyId);
            } catch (PersistenceException e) {
                LOG.debugf("Idempotent entry collision for %s — falling through to attestations", entryId);
            }
        }

        for (AttestationIntent intent : intents) {
            intentWriter.write(intent, tenancyId);
        }

        return entryId;
    }

    private void assertNoActiveTransaction() {
        try {
            int status = tm.getStatus();
            if (status != jakarta.transaction.Status.STATUS_NO_TRANSACTION) {
                throw new IllegalStateException(
                        "writeOutcome() must not be called within an active transaction — status=" + status);
            }
        } catch (SystemException e) {
            throw new IllegalStateException("Cannot verify transaction status", e);
        }
    }

    private static UUID extractCaseId(PrLifecycleEvent event) {
        if (event instanceof PrLifecycleEvent.Merged m) return m.caseId();
        if (event instanceof PrLifecycleEvent.Rejected r) return r.caseId();
        if (event instanceof PrLifecycleEvent.ChangesRequested c) return c.caseId();
        return null;
    }
}
