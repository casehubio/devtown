package io.casehub.devtown.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.MapPreferences;
import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.BreachType;
import io.casehub.work.api.BreachedTask;
import io.casehub.work.api.SlaBreachContext;
import io.casehub.work.runtime.event.SlaBreachEvent;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MergeQueueSlaBreachTest {

    MergeQueueSlaBreachObserver observer;
    MergeQueueService mergeQueueService;

    @BeforeEach
    void setUp() {
        mergeQueueService = mock(MergeQueueService.class);
        observer = new MergeQueueSlaBreachObserver();
        // inject mock via reflection
        try {
            var field = MergeQueueSlaBreachObserver.class.getDeclaredField("mergeQueueService");
            field.setAccessible(true);
            field.set(observer, mergeQueueService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void mergeQueueCallerRef_triggersPrioritize() {
        var event = buildEvent("casehubio/devtown#456");
        observer.onBreach(event);
        verify(mergeQueueService).prioritize(456, "casehubio/devtown");
    }

    @Test
    void prReviewCallerRef_ignored() {
        var event = buildEvent("case:" + UUID.randomUUID() + ":plan-item:pi-123");
        observer.onBreach(event);
        verifyNoInteractions(mergeQueueService);
    }

    @Test
    void nullCallerRef_ignored() {
        var event = buildEvent(null);
        observer.onBreach(event);
        verifyNoInteractions(mergeQueueService);
    }

    @Test
    void hashOnly_ignored() {
        var event = buildEvent("#456");
        observer.onBreach(event);
        verifyNoInteractions(mergeQueueService);
    }

    @Test
    void nonNumericPr_ignored() {
        var event = buildEvent("casehubio/devtown#abc");
        observer.onBreach(event);
        verifyNoInteractions(mergeQueueService);
    }

    private SlaBreachEvent buildEvent(String callerRef) {
        var task = new BreachedTask(UUID.randomUUID(), callerRef, "test", Set.of());
        var ctx = new SlaBreachContext(
            BreachType.COMPLETION_EXPIRED, task,
            Path.of("casehubio", "devtown", "merge-queue"),
            new MapPreferences(Map.of())
        );
        return new SlaBreachEvent(ctx, new BreachDecision.Fail("sla-breach"), "default");
    }
}
