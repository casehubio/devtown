package io.casehub.devtown.review.notification;

import io.casehub.platform.api.subscription.SubscribableEvent;

public record StalledCommitmentEvent(
    String conditionType,
    String targetName,
    String summary,
    String firedAt,
    String actorId,
    String targetChannel,
    String tenancyId
) implements SubscribableEvent {
    @Override public String type() { return "io.casehub.devtown.commitment.stalled"; }
    @Override public String tenancyId() { return tenancyId; }
}
