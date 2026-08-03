package io.casehub.devtown.app.trust;

import io.casehub.blocks.attestation.AttestationIntent;
import io.casehub.devtown.app.ledger.ContributorOutcomeLedgerWriter;
import io.casehub.devtown.review.PrLifecycleEvent;
import io.casehub.ledger.api.model.AttestationVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrLifecycleAttestationObserverTest {

    private ContributorOutcomeLedgerWriter writer;
    private ContributorAttestationPolicy policy;
    private PrLifecycleAttestationObserver observer;

    private final List<String> writtenOutcomes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        writer = mock(ContributorOutcomeLedgerWriter.class);
        policy = new ContributorAttestationPolicy();
        observer = new PrLifecycleAttestationObserver();
        observer.writer = writer;
        observer.policy = policy;

        when(writer.writeOutcome(any(), anyString(), any(), any()))
                .thenAnswer(inv -> {
                    writtenOutcomes.add(inv.getArgument(1));
                    return UUID.randomUUID();
                });
    }

    @Test
    void merged_noRework_outcomeIsMerged() {
        var event = new PrLifecycleEvent.Merged("repo/name", 42, "github-id:123", UUID.randomUUID(), 0);
        observer.onMerged(event);

        assertThat(writtenOutcomes).containsExactly("MERGED");
        verify(writer).writeOutcome(eq(event), eq("MERGED"), eq(0), any());
    }

    @Test
    void merged_withRework_outcomeIsMergedWithRework() {
        var event = new PrLifecycleEvent.Merged("repo/name", 42, "github-id:123", UUID.randomUUID(), 2);
        observer.onMerged(event);

        assertThat(writtenOutcomes).containsExactly("MERGED_WITH_REWORK");
        verify(writer).writeOutcome(eq(event), eq("MERGED_WITH_REWORK"), eq(2), any());
    }

    @Test
    void rejected_byMaintainer_outcomeIsRejected() {
        var event = new PrLifecycleEvent.Rejected("repo/name", 42, "github-id:123",
                UUID.randomUUID(), "maintainer", 456L, "User", 0);
        observer.onRejected(event);

        assertThat(writtenOutcomes).containsExactly("REJECTED");
    }

    @Test
    void rejected_byAuthor_noReviewRounds_noAttestation() {
        var event = new PrLifecycleEvent.Rejected("repo/name", 42, "github-id:123",
                UUID.randomUUID(), "author", 123L, "User", 0);
        observer.onRejected(event);

        assertThat(writtenOutcomes).isEmpty();
        verify(writer, never()).writeOutcome(any(), anyString(), any(), any());
    }

    @Test
    void rejected_byAuthor_withReviewRounds_outcomeIsAbandonedAfterReview() {
        var event = new PrLifecycleEvent.Rejected("repo/name", 42, "github-id:123",
                UUID.randomUUID(), "author", 123L, "User", 2);
        observer.onRejected(event);

        assertThat(writtenOutcomes).containsExactly("ABANDONED_AFTER_REVIEW");
    }

    @Test
    void rejected_byBot_noReviewRounds_noAttestation() {
        var event = new PrLifecycleEvent.Rejected("repo/name", 42, "github-id:123",
                UUID.randomUUID(), "stale-bot", 789L, "Bot", 0);
        observer.onRejected(event);

        assertThat(writtenOutcomes).isEmpty();
    }

    @Test
    void rejected_byBot_withReviewRounds_outcomeIsAbandonedAfterReview() {
        var event = new PrLifecycleEvent.Rejected("repo/name", 42, "github-id:123",
                UUID.randomUUID(), "stale-bot", 789L, "Bot", 1);
        observer.onRejected(event);

        assertThat(writtenOutcomes).containsExactly("ABANDONED_AFTER_REVIEW");
    }

    @Test
    void changesRequested_outcomeIsChangesRequested() {
        var event = new PrLifecycleEvent.ChangesRequested("repo/name", 42, "github-id:123", UUID.randomUUID());
        observer.onChangesRequested(event);

        assertThat(writtenOutcomes).containsExactly("CHANGES_REQUESTED");
    }

    @Test
    void triageRejected_outcomeIsTriageRejected() {
        var event = new PrLifecycleEvent.TriageRejected("repo/name", 42, "github-id:123", "spam");
        observer.onTriageRejected(event);

        assertThat(writtenOutcomes).containsExactly("TRIAGE_REJECTED");
    }

    @Test
    void merged_intentsPassedToWriter_haveCorrectVerdict() {
        var event = new PrLifecycleEvent.Merged("repo/name", 42, "github-id:123", UUID.randomUUID(), 0);

        when(writer.writeOutcome(any(), anyString(), any(), any()))
                .thenAnswer(inv -> {
                    List<AttestationIntent> intents = inv.getArgument(3);
                    assertThat(intents).hasSize(1);
                    assertThat(intents.get(0).verdict()).isEqualTo(AttestationVerdict.SOUND);
                    assertThat(intents.get(0).confidence()).isEqualTo(1.0);
                    return UUID.randomUUID();
                });

        observer.onMerged(event);
    }
}
