package io.casehub.devtown.app.trust;

import io.casehub.agency.governance.attestation.AttestationIntent;
import io.casehub.devtown.domain.ContributorTrustCapability;
import io.casehub.devtown.domain.ContributorTrustDimension;
import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.review.PrLifecycleEvent;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContributorAttestationPolicyTest {

    private final ContributorAttestationPolicy policy = new ContributorAttestationPolicy();

    @Test
    void merged_firstAttempt_soundWithBothDimensions() {
        var event = new PrLifecycleEvent.Merged("repo/name", 42, "github-id:123", UUID.randomUUID(), 0, Set.of());
        List<AttestationIntent> intents = policy.evaluate(event, "MERGED", 0);

        assertThat(intents).hasSize(1);
        AttestationIntent intent = intents.get(0);
        assertThat(intent.verdict()).isEqualTo(AttestationVerdict.SOUND);
        assertThat(intent.confidence()).isEqualTo(1.0);
        assertThat(intent.capabilityTag()).isEqualTo(ContributorTrustCapability.PR_CONTRIBUTION);
        assertThat(intent.dimensions()).containsEntry(ContributorTrustDimension.MERGE_RATE, 1.0);
        assertThat(intent.dimensions()).containsEntry(ContributorTrustDimension.FIRST_ATTEMPT_QUALITY, 1.0);
    }

    @Test
    void merged_withRework_soundWithMergeRateOnly() {
        var event = new PrLifecycleEvent.Merged("repo/name", 42, "github-id:123", UUID.randomUUID(), 2, Set.of());
        List<AttestationIntent> intents = policy.evaluate(event, "MERGED_WITH_REWORK", 2);

        assertThat(intents).hasSize(1);
        assertThat(intents.get(0).verdict()).isEqualTo(AttestationVerdict.SOUND);
        assertThat(intents.get(0).confidence()).isEqualTo(0.7);
        assertThat(intents.get(0).dimensions()).containsEntry(ContributorTrustDimension.MERGE_RATE, 1.0);
        assertThat(intents.get(0).dimensions()).doesNotContainKey(ContributorTrustDimension.FIRST_ATTEMPT_QUALITY);
    }

    @Test
    void changesRequested_flaggedWithFirstAttemptQuality() {
        var event = new PrLifecycleEvent.ChangesRequested("repo/name", 42, "github-id:123", UUID.randomUUID(), Set.of());
        List<AttestationIntent> intents = policy.evaluate(event, "CHANGES_REQUESTED", null);

        assertThat(intents).hasSize(1);
        assertThat(intents.get(0).verdict()).isEqualTo(AttestationVerdict.FLAGGED);
        assertThat(intents.get(0).confidence()).isEqualTo(0.5);
        assertThat(intents.get(0).dimensions()).containsEntry(ContributorTrustDimension.FIRST_ATTEMPT_QUALITY, 0.0);
    }

    @Test
    void rejected_flaggedWithMergeRate() {
        var event = new PrLifecycleEvent.Rejected("repo/name", 42, "github-id:123",
                UUID.randomUUID(), "maintainer", 456L, "User", 0, Set.of());
        List<AttestationIntent> intents = policy.evaluate(event, "REJECTED", null);

        assertThat(intents).hasSize(1);
        assertThat(intents.get(0).verdict()).isEqualTo(AttestationVerdict.FLAGGED);
        assertThat(intents.get(0).confidence()).isEqualTo(1.0);
        assertThat(intents.get(0).dimensions()).containsEntry(ContributorTrustDimension.MERGE_RATE, 0.0);
    }

    @Test
    void triageRejected_flaggedLowConfidence() {
        var event = new PrLifecycleEvent.TriageRejected("repo/name", 42, "github-id:123", "spam");
        List<AttestationIntent> intents = policy.evaluate(event, "TRIAGE_REJECTED", null);

        assertThat(intents).hasSize(1);
        assertThat(intents.get(0).verdict()).isEqualTo(AttestationVerdict.FLAGGED);
        assertThat(intents.get(0).confidence()).isEqualTo(0.3);
        assertThat(intents.get(0).dimensions()).containsEntry(ContributorTrustDimension.MERGE_RATE, 0.0);
    }

    @Test
    void abandonedAfterReview_flaggedLowConfidence() {
        var event = new PrLifecycleEvent.Rejected("repo/name", 42, "github-id:123",
                UUID.randomUUID(), "author", 123L, "User", 1, Set.of());
        List<AttestationIntent> intents = policy.evaluate(event, "ABANDONED_AFTER_REVIEW", 1);

        assertThat(intents).hasSize(1);
        assertThat(intents.get(0).verdict()).isEqualTo(AttestationVerdict.FLAGGED);
        assertThat(intents.get(0).confidence()).isEqualTo(0.3);
        assertThat(intents.get(0).dimensions()).containsEntry(ContributorTrustDimension.FIRST_ATTEMPT_QUALITY, 0.0);
    }

    @Test
    void allIntents_haveSystemAttestorAndDeterministicNamespace() {
        var event = new PrLifecycleEvent.Merged("repo/name", 42, "github-id:123", UUID.randomUUID(), 0, Set.of());
        List<AttestationIntent> intents = policy.evaluate(event, "MERGED", 0);

        assertThat(intents.get(0).attestorId()).isEqualTo("system:devtown");
        assertThat(intents.get(0).actorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(intents.get(0).attestorRole()).isEqualTo("lifecycle-pipeline");
        assertThat(intents.get(0).namespace()).isNotNull();
    }

    @Test
    void deterministicEntryId_consistentAcrossCalls() {
        UUID id1 = ContributorAttestationPolicy.deterministicEntryId("repo/name", 42, "MERGED");
        UUID id2 = ContributorAttestationPolicy.deterministicEntryId("repo/name", 42, "MERGED");
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void deterministicEntryId_differentOutcomesDifferentIds() {
        UUID merged = ContributorAttestationPolicy.deterministicEntryId("repo/name", 42, "MERGED");
        UUID rejected = ContributorAttestationPolicy.deterministicEntryId("repo/name", 42, "REJECTED");
        assertThat(merged).isNotEqualTo(rejected);
    }

    @Test
    void evidence_rejected_includesSenderLogin() {
        var event = new PrLifecycleEvent.Rejected("repo/name", 42, "github-id:123",
                UUID.randomUUID(), "alice", 456L, "User", 0, Set.of());
        List<AttestationIntent> intents = policy.evaluate(event, "REJECTED", null);
        assertThat(intents.get(0).evidence()).contains("@alice");
    }

    @Test
    void subjectId_triageRejected_isSyntheticPrUuid() {
        var event = new PrLifecycleEvent.TriageRejected("repo/name", 42, "github-id:123", "spam");
        UUID subjectId = ContributorAttestationPolicy.resolveSubjectId(event);
        UUID expected = UUID.nameUUIDFromBytes("pr:repo/name:42".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(subjectId).isEqualTo(expected);
    }

    @Test
    void merged_withTwoDomains_producesThreeAttestations() {
        var domains = Set.of(ReviewDomain.SECURITY_REVIEW, ReviewDomain.ARCHITECTURE_REVIEW);
        var event = new PrLifecycleEvent.Merged("repo/name", 42, "github-id:123",
                                                UUID.randomUUID(), 0, domains);
        List<AttestationIntent> intents = policy.evaluate(event, "MERGED", 0, domains);

        assertThat(intents).hasSize(3);
        assertThat(intents.stream().map(AttestationIntent::capabilityTag).toList())
                .containsExactlyInAnyOrder(
                        ContributorTrustCapability.PR_CONTRIBUTION,
                        ContributorTrustCapability.forDomain(ReviewDomain.SECURITY_REVIEW),
                        ContributorTrustCapability.forDomain(ReviewDomain.ARCHITECTURE_REVIEW));
    }

    @Test
    void merged_emptyDomains_producesAggregateOnly() {
        var event = new PrLifecycleEvent.Merged("repo/name", 42, "github-id:123",
                                                UUID.randomUUID(), 0, Set.of());
        List<AttestationIntent> intents = policy.evaluate(event, "MERGED", 0, Set.of());

        assertThat(intents).hasSize(1);
        assertThat(intents.get(0).capabilityTag())
                .isEqualTo(ContributorTrustCapability.PR_CONTRIBUTION);
    }

    @Test
    void perDomain_entryIds_differFromAggregate() {
        var domains = Set.of(ReviewDomain.SECURITY_REVIEW);
        var event = new PrLifecycleEvent.Merged("repo/name", 42, "github-id:123",
                                                UUID.randomUUID(), 0, domains);
        List<AttestationIntent> intents = policy.evaluate(event, "MERGED", 0, domains);

        UUID aggregateId = intents.stream()
                                  .filter(i -> i.capabilityTag().equals(ContributorTrustCapability.PR_CONTRIBUTION))
                                  .findFirst().orElseThrow().entryId();
        UUID domainId = intents.stream()
                               .filter(i -> i.capabilityTag().contains("security"))
                               .findFirst().orElseThrow().entryId();
        assertThat(aggregateId).isNotEqualTo(domainId);
    }

    @Test
    void perDomain_sameVerdictAndDimensionsAsAggregate() {
        var domains = Set.of(ReviewDomain.SECURITY_REVIEW);
        var event = new PrLifecycleEvent.Merged("repo/name", 42, "github-id:123",
                                                UUID.randomUUID(), 0, domains);
        List<AttestationIntent> intents = policy.evaluate(event, "MERGED", 0, domains);

        AttestationIntent aggregate = intents.stream()
                                             .filter(i -> i.capabilityTag().equals(ContributorTrustCapability.PR_CONTRIBUTION))
                                             .findFirst().orElseThrow();
        AttestationIntent domain = intents.stream()
                                          .filter(i -> i.capabilityTag().contains("security"))
                                          .findFirst().orElseThrow();

        assertThat(domain.verdict()).isEqualTo(aggregate.verdict());
        assertThat(domain.confidence()).isEqualTo(aggregate.confidence());
        assertThat(domain.dimensions()).isEqualTo(aggregate.dimensions());
    }

    @Test
    void deterministicDomainEntryId_consistentAcrossCalls() {
        UUID id1 = ContributorAttestationPolicy.deterministicDomainEntryId("repo/name", 42, "MERGED", "security-review");
        UUID id2 = ContributorAttestationPolicy.deterministicDomainEntryId("repo/name", 42, "MERGED", "security-review");
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void deterministicDomainEntryId_differentDomainsDifferentIds() {
        UUID security     = ContributorAttestationPolicy.deterministicDomainEntryId("repo/name", 42, "MERGED", "security-review");
        UUID architecture = ContributorAttestationPolicy.deterministicDomainEntryId("repo/name", 42, "MERGED", "architecture-review");
        assertThat(security).isNotEqualTo(architecture);
    }

    @Test
    void deterministicDomainEntryId_differsFromAggregateEntryId() {
        UUID aggregate = ContributorAttestationPolicy.deterministicEntryId("repo/name", 42, "MERGED");
        UUID domain    = ContributorAttestationPolicy.deterministicDomainEntryId("repo/name", 42, "MERGED", "security-review");
        assertThat(aggregate).isNotEqualTo(domain);
    }
}
