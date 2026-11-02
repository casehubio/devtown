package io.casehub.devtown.review;

import java.util.Set;
import java.util.UUID;

public sealed interface PrLifecycleEvent {
    String repo();
    int prNumber();
    String contributorId();
    Set<String> reviewDomains();

    record Merged(String repo, int prNumber, String contributorId,
                  UUID caseId, int reviewRounds,
                  Set<String> reviewDomains) implements PrLifecycleEvent {}

    record Rejected(String repo, int prNumber, String contributorId,
                    UUID caseId, String senderLogin, long senderId,
                    String senderType, int reviewRounds,
                    Set<String> reviewDomains) implements PrLifecycleEvent {}

    record ChangesRequested(String repo, int prNumber, String contributorId,
                            UUID caseId,
                            Set<String> reviewDomains) implements PrLifecycleEvent {}

    record TriageRejected(String repo, int prNumber, String contributorId,
                          String reason) implements PrLifecycleEvent {
        @Override public Set<String> reviewDomains() { return Set.of(); }
    }
}
