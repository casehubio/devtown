package io.casehub.devtown.app.trust;

import io.casehub.blocks.attestation.AttestationIntent;
import io.casehub.devtown.domain.ContributorTrustCapability;
import io.casehub.devtown.domain.ContributorTrustDimension;
import io.casehub.devtown.review.PrLifecycleEvent;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.platform.api.identity.ActorType;

import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ContributorAttestationPolicy {

    static final String ATTESTOR_ID = "system:devtown";
    static final String ATTESTOR_ROLE = "lifecycle-pipeline";

    static final UUID CONTRIBUTOR_OUTCOME_NS =
            UUID.nameUUIDFromBytes("casehub.io/devtown/contributor-outcome".getBytes(StandardCharsets.UTF_8));

    public List<AttestationIntent> evaluate(PrLifecycleEvent event,
                                             String outcome,
                                             Integer reviewRounds) {
        UUID entryId = deterministicEntryId(event.repo(), event.prNumber(), outcome);
        UUID subjectId = resolveSubjectId(event);

        return switch (outcome) {
            case "MERGED" -> List.of(intent(entryId, subjectId,
                    AttestationVerdict.SOUND, 1.0,
                    Map.of(ContributorTrustDimension.MERGE_RATE, 1.0,
                           ContributorTrustDimension.FIRST_ATTEMPT_QUALITY, 1.0),
                    evidence(event, outcome)));

            case "MERGED_WITH_REWORK" -> List.of(intent(entryId, subjectId,
                    AttestationVerdict.SOUND, 0.7,
                    Map.of(ContributorTrustDimension.MERGE_RATE, 1.0),
                    evidence(event, outcome)));

            case "CHANGES_REQUESTED" -> List.of(intent(entryId, subjectId,
                    AttestationVerdict.FLAGGED, 0.5,
                    Map.of(ContributorTrustDimension.FIRST_ATTEMPT_QUALITY, 0.0),
                    evidence(event, outcome)));

            case "REJECTED" -> List.of(intent(entryId, subjectId,
                    AttestationVerdict.FLAGGED, 1.0,
                    Map.of(ContributorTrustDimension.MERGE_RATE, 0.0),
                    evidence(event, outcome)));

            case "TRIAGE_REJECTED" -> List.of(intent(entryId, subjectId,
                    AttestationVerdict.FLAGGED, 0.3,
                    Map.of(ContributorTrustDimension.MERGE_RATE, 0.0),
                    evidence(event, outcome)));

            case "ABANDONED_AFTER_REVIEW" -> List.of(intent(entryId, subjectId,
                    AttestationVerdict.FLAGGED, 0.3,
                    Map.of(ContributorTrustDimension.FIRST_ATTEMPT_QUALITY, 0.0),
                    evidence(event, outcome)));

            default -> List.of();
        };
    }

    private AttestationIntent intent(UUID entryId, UUID subjectId,
                                      AttestationVerdict verdict, double confidence,
                                      Map<String, Double> dimensions, String evidence) {
        return new AttestationIntent(
                entryId, subjectId,
                verdict, confidence,
                ContributorTrustCapability.PR_CONTRIBUTION,
                ATTESTOR_ID, ActorType.SYSTEM, ATTESTOR_ROLE,
                dimensions, evidence,
                CONTRIBUTOR_OUTCOME_NS, null);
    }

    public static UUID deterministicEntryId(String repo, int prNumber, String outcome) {
        String key = CONTRIBUTOR_OUTCOME_NS + "|" + repo + ":" + prNumber + ":" + outcome;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    public static UUID resolveSubjectId(PrLifecycleEvent event) {
        if (event instanceof PrLifecycleEvent.Merged m) return m.caseId();
        if (event instanceof PrLifecycleEvent.Rejected r) return r.caseId();
        if (event instanceof PrLifecycleEvent.ChangesRequested c) return c.caseId();
        return UUID.nameUUIDFromBytes(
                ("pr:" + event.repo() + ":" + event.prNumber()).getBytes(StandardCharsets.UTF_8));
    }

    private String evidence(PrLifecycleEvent event, String outcome) {
        String base = "PR #" + event.prNumber() + " " + outcome.toLowerCase().replace('_', ' ')
                + " in " + event.repo();
        if (event instanceof PrLifecycleEvent.Rejected r && "REJECTED".equals(outcome)) {
            return base + " by @" + r.senderLogin();
        }
        return base;
    }
}
