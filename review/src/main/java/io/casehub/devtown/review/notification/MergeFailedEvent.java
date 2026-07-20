package io.casehub.devtown.review.notification;

import io.casehub.platform.api.subscription.SubscribableEvent;

public record MergeFailedEvent(
    String prNumber,
    String prTitle,
    String authorId,
    String authorName,
    String failureReason,
    String repoId,
    String prUrl,
    String targetChannel,
    String tenancyId
) implements SubscribableEvent {
    @Override public String type() { return "io.casehub.devtown.merge.failed"; }
    @Override public String tenancyId() { return tenancyId; }
}
