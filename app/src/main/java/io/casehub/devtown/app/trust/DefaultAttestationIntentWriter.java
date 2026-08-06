package io.casehub.devtown.app.trust;

import io.casehub.blocks.attestation.AttestationIntent;
import io.casehub.blocks.attestation.AttestationIntentWriter;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
@DefaultBean
public class DefaultAttestationIntentWriter implements AttestationIntentWriter {

    private static final Logger LOG = Logger.getLogger(DefaultAttestationIntentWriter.class);

    @Inject LedgerEntryRepository ledgerRepo;

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void write(AttestationIntent intent, String tenancyId) {
        LedgerAttestation a = new LedgerAttestation();
        a.id = UUID.nameUUIDFromBytes(
                (intent.namespace() + "|" + intent.entryId() + "|" + intent.capabilityTag())
                        .getBytes(StandardCharsets.UTF_8));
        a.ledgerEntryId = intent.entryId();
        a.subjectId = intent.subjectId();
        a.attestorId = intent.attestorId();
        a.attestorType = intent.actorType();
        a.attestorRole = intent.attestorRole();
        a.verdict = intent.verdict();
        a.evidence = intent.evidence();
        a.confidence = intent.confidence();
        a.capabilityTag = intent.capabilityTag();
        a.occurredAt = Instant.now();
        try {
            ledgerRepo.saveAttestation(a, tenancyId);
        } catch (jakarta.persistence.PersistenceException e) {
            LOG.debugf("Idempotent attestation collision for %s — skipping", a.id);
        }
    }
}
