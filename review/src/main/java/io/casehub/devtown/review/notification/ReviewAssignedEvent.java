package io.casehub.devtown.review.notification;

import io.casehub.platform.api.subscription.SubscribableEvent;

public record ReviewAssignedEvent(
    String workItemId,
    String assigneeId,
    String targetChannel,
    String tenancyId
) implements SubscribableEvent {
    @Override public String type() { return "io.casehub.devtown.review.assigned"; }
    @Override public String tenancyId() { return tenancyId; }
}
