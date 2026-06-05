package io.casehub.devtown.app;

import io.casehub.devtown.review.ReviewCompletedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-scope bean that captures async ReviewCompletedEvent deliveries.
 * Used by ReviewOutcomeObserverTest to verify event production.
 *
 * <p>All access to the captured events MUST go through methods, not direct
 * field access — CDI client proxies only delegate method calls to the
 * contextual instance.
 */
@ApplicationScoped
class ReviewCompletedEventCapture {

    private final CopyOnWriteArrayList<ReviewCompletedEvent> events = new CopyOnWriteArrayList<>();

    void onEvent(@ObservesAsync ReviewCompletedEvent event) {
        events.add(event);
    }

    List<ReviewCompletedEvent> getEvents() {
        return events;
    }

    void clear() {
        events.clear();
    }
}
