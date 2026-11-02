package io.casehub.devtown.app.trust;

import io.casehub.agency.governance.attestation.AttestationIntent;
import io.casehub.devtown.domain.ContributorTrustCapability;
import io.casehub.devtown.domain.ContributorTrustDimension;
import io.casehub.devtown.review.PrLifecycleEvent;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.platform.api.identity.ActorType;

import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        return evaluate(event, outcome, reviewRounds, Set.of());
    }

    public List<AttestationIntent> evaluate(PrLifecycleEvent event,
                                             String outcome,
                                             Integer reviewRounds,
                                             Set<String> reviewDomains) {
        UUID subjectId = resolveSubjectId(event);
        List<AttestationIntent> result = new ArrayList<>();

        UUID aggregateEntryId = deterministicEntryId(event.repo(), event.prNumber(), outcome);
        addOutcomeIntent(result, aggregateEntryId, subjectId,
                ContributorTrustCapability.PR_CONTRIBUTION, outcome, event);

        for (String domain : reviewDomains) {
            UUID domainEntryId = deterministicDomainEntryId(
                    event.repo(), event.prNumber(), outcome, domain);
            addOutcomeIntent(result, domainEntryId, subjectId,
                    ContributorTrustCapability.forDomain(domain), outcome, event);
        }
        return result;
    }

    private void addOutcomeIntent(List<AttestationIntent> result, UUID entryId,
                                   UUID subjectId, String capabilityTag,
                                   String outcome, PrLifecycleEvent event) {
        switch (outcome) {
            case "MERGED" -> result.add(intent(entryId, subjectId, capabilityTag,
                    AttestationVerdict.SOUND, 1.0,
                    Map.of(ContributorTrustDimension.MERGE_RATE, 1.0,
                           ContributorTrustDimension.FIRST_ATTEMPT_QUALITY, 1.0),
                    evidence(event, outcome)));
            case "MERGED_WITH_REWORK" -> result.add(intent(entryId, subjectId, capabilityTag,
                    AttestationVerdict.SOUND, 0.7,
                    Map.of(ContributorTrustDimension.MERGE_RATE, 1.0),
                    evidence(event, outcome)));
            case "CHANGES_REQUESTED" -> result.add(intent(entryId, subjectId, capabilityTag,
                    AttestationVerdict.FLAGGED, 0.5,
                    Map.of(ContributorTrustDimension.FIRST_ATTEMPT_QUALITY, 0.0),
                    evidence(event, outcome)));
            case "REJECTED" -> result.add(intent(entryId, subjectId, capabilityTag,
                    AttestationVerdict.FLAGGED, 1.0,
                    Map.of(ContributorTrustDimension.MERGE_RATE, 0.0),
                    evidence(event, outcome)));
            case "TRIAGE_REJECTED" -> result.add(intent(entryId, subjectId, capabilityTag,
                    AttestationVerdict.FLAGGED, 0.3,
                    Map.of(ContributorTrustDimension.MERGE_RATE, 0.0),
                    evidence(event, outcome)));
            case "ABANDONED_AFTER_REVIEW" -> result.add(intent(entryId, subjectId, capabilityTag,
                    AttestationVerdict.FLAGGED, 0.3,
                    Map.of(ContributorTrustDimension.FIRST_ATTEMPT_QUALITY, 0.0),
                    evidence(event, outcome)));
            default -> { }
        }
    }

    private AttestationIntent intent(UUID entryId, UUID subjectId, String capabilityTag,
                                      AttestationVerdict verdict, double confidence,
                                      Map<String, Double> dimensions, String evidence) {
        return new AttestationIntent(
                entryId, subjectId,
                verdict, confidence,
                capabilityTag,
                ATTESTOR_ID, ActorType.SYSTEM, ATTESTOR_ROLE,
                dimensions, evidence,
                CONTRIBUTOR_OUTCOME_NS, null);
    }

    public static UUID deterministicDomainEntryId(String repo, int prNumber,
                                                   String outcome, String domain) {
        String key = CONTRIBUTOR_OUTCOME_NS + "|" + repo + ":" + prNumber
                + ":" + outcome + ":" + domain;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
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
