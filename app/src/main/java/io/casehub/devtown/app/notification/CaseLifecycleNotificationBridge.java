package io.casehub.devtown.app.notification;

import io.casehub.devtown.domain.notification.NotificationPreferenceKeys;
import io.casehub.devtown.review.notification.CaseFaultedEvent;
import io.casehub.devtown.review.notification.MergeFailedEvent;
import io.casehub.devtown.review.notification.MergeSucceededEvent;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CaseLifecycleNotificationBridge {

    private final Event<MergeSucceededEvent> mergeSucceededEvents;
    private final Event<MergeFailedEvent> mergeFailedEvents;
    private final Event<CaseFaultedEvent> caseFaultedEvents;
    private final PreferenceProvider preferenceProvider;

    @Inject
    public CaseLifecycleNotificationBridge(
            Event<MergeSucceededEvent> mergeSucceededEvents,
            Event<MergeFailedEvent> mergeFailedEvents,
            Event<CaseFaultedEvent> caseFaultedEvents,
            PreferenceProvider preferenceProvider) {
        this.mergeSucceededEvents = mergeSucceededEvents;
        this.mergeFailedEvents = mergeFailedEvents;
        this.caseFaultedEvents = caseFaultedEvents;
        this.preferenceProvider = preferenceProvider;
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        if (event.caseStatus() == null) return;
        String targetChannel = resolveChannel(event.namespace());
        switch (event.caseStatus()) {
            case "COMPLETED" -> mergeSucceededEvents.fire(new MergeSucceededEvent(
                event.caseId().toString(),
                event.caseDefinitionName(),
                null, null, null,
                event.actorId(),
                targetChannel,
                event.tenancyId()));
            case "CANCELLED" -> mergeFailedEvents.fire(new MergeFailedEvent(
                null, null, null, null,
                event.satisfiedGoalName(),
                event.namespace(),
                null,
                targetChannel,
                event.tenancyId()));
            case "FAULTED" -> caseFaultedEvents.fire(new CaseFaultedEvent(
                event.caseId().toString(),
                event.caseDefinitionName(),
                event.caseStatus(),
                event.contextSnapshot() != null ? event.contextSnapshot().toString() : null,
                targetChannel,
                event.tenancyId()));
            default -> { }
        }
    }

    private String resolveChannel(String namespace) {
        return preferenceProvider
            .resolve(namespace != null ? SettingsScope.of(namespace) : SettingsScope.root())
            .getOrDefault(NotificationPreferenceKeys.SLACK_CHANNEL).value();
    }
}
