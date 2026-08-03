package io.casehub.devtown.review;

import java.util.UUID;

public sealed interface PrLifecycleEvent {
    String repo();
    int prNumber();
    String contributorId();

    record Merged(String repo, int prNumber, String contributorId,
                  UUID caseId, int reviewRounds) implements PrLifecycleEvent {}

    record Rejected(String repo, int prNumber, String contributorId,
                    UUID caseId, String senderLogin, long senderId,
                    String senderType, int reviewRounds) implements PrLifecycleEvent {}

    record ChangesRequested(String repo, int prNumber, String contributorId,
                            UUID caseId) implements PrLifecycleEvent {}

    record TriageRejected(String repo, int prNumber, String contributorId,
                          String reason) implements PrLifecycleEvent {}
}
