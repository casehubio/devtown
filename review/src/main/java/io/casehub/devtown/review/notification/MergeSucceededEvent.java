package io.casehub.devtown.review.notification;

import io.casehub.platform.api.subscription.SubscribableEvent;

public record MergeSucceededEvent(
    String caseId,
    String caseDefinitionName,
    String prNumber,
    String prCount,
    String prList,
    String actorId,
    String targetChannel,
    String tenancyId
) implements SubscribableEvent {
    @Override public String type() { return "io.casehub.devtown.merge.succeeded"; }
    @Override public String tenancyId() { return tenancyId; }
}
