package io.casehub.devtown.app.trust;

import io.casehub.blocks.attestation.AttestationIntent;
import io.casehub.devtown.app.ledger.ContributorOutcomeLedgerWriter;
import io.casehub.devtown.review.PrLifecycleEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class PrLifecycleAttestationObserver {

    @Inject ContributorOutcomeLedgerWriter writer;
    @Inject ContributorAttestationPolicy policy;

    void onMerged(@Observes @Priority(100) PrLifecycleEvent.Merged event) {
        String outcome = event.reviewRounds() > 0 ? "MERGED_WITH_REWORK" : "MERGED";
        List<AttestationIntent> intents = policy.evaluate(event, outcome, event.reviewRounds());
        writer.writeOutcome(event, outcome, event.reviewRounds(), intents);
    }

    void onRejected(@Observes @Priority(100) PrLifecycleEvent.Rejected event) {
        if ("Bot".equals(event.senderType())) {
            handleBotOrAuthorClosure(event);
            return;
        }
        String senderActorId = "github-id:" + event.senderId();
        boolean isMaintainerRejection = !senderActorId.equals(event.contributorId());
        if (isMaintainerRejection) {
            List<AttestationIntent> intents = policy.evaluate(event, "REJECTED", null);
            writer.writeOutcome(event, "REJECTED", null, intents);
        } else {
            handleBotOrAuthorClosure(event);
        }
    }

    void onChangesRequested(@Observes @Priority(100) PrLifecycleEvent.ChangesRequested event) {
        List<AttestationIntent> intents = policy.evaluate(event, "CHANGES_REQUESTED", null);
        writer.writeOutcome(event, "CHANGES_REQUESTED", null, intents);
    }

    void onTriageRejected(@Observes @Priority(100) PrLifecycleEvent.TriageRejected event) {
        List<AttestationIntent> intents = policy.evaluate(event, "TRIAGE_REJECTED", null);
        writer.writeOutcome(event, "TRIAGE_REJECTED", null, intents);
    }

    private void handleBotOrAuthorClosure(PrLifecycleEvent.Rejected event) {
        if (event.reviewRounds() > 0) {
            List<AttestationIntent> intents = policy.evaluate(
                    event, "ABANDONED_AFTER_REVIEW", event.reviewRounds());
            writer.writeOutcome(event, "ABANDONED_AFTER_REVIEW", event.reviewRounds(), intents);
        }
    }
}
