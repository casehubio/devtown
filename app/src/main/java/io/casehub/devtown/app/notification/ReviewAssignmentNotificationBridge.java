package io.casehub.devtown.app.notification;

import io.casehub.devtown.domain.notification.NotificationPreferenceKeys;
import io.casehub.devtown.review.notification.ReviewAssignedEvent;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.SettingsScope;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ReviewAssignmentNotificationBridge {

    private final Event<ReviewAssignedEvent> reviewAssignedEvents;
    private final PreferenceProvider preferenceProvider;

    @Inject
    public ReviewAssignmentNotificationBridge(
            Event<ReviewAssignedEvent> reviewAssignedEvents,
            PreferenceProvider preferenceProvider) {
        this.reviewAssignedEvents = reviewAssignedEvents;
        this.preferenceProvider = preferenceProvider;
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    void onWorkItemCreated(
            @Observes(during = TransactionPhase.AFTER_SUCCESS)
            WorkItemLifecycleEvent event) {
        if (!event.type().endsWith(".created")) return;
        if (event.types() == null || !event.types().contains("human-decision:pr-approval")) return;
        String targetChannel = preferenceProvider
            .resolve(event.workItem() != null && event.workItem().scope != null
                ? SettingsScope.of(event.workItem().scope)
                : SettingsScope.root())
            .getOrDefault(NotificationPreferenceKeys.SLACK_CHANNEL).value();
        reviewAssignedEvents.fire(new ReviewAssignedEvent(
            event.workItemId().toString(),
            event.assigneeId(),
            targetChannel,
            event.tenancyId()));
    }
}
