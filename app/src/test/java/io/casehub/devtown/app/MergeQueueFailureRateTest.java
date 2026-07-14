package io.casehub.devtown.app;

import io.casehub.devtown.merge.BatchRecord;
import io.casehub.devtown.merge.MergeQueueStore;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MergeQueueFailureRateTest {

    private MergeQueueStore store;
    private MergeQueueService service;

    @BeforeEach
    void setUp() throws Exception {
        store = mock(MergeQueueStore.class);
        var prefs = mock(PreferenceProvider.class);
        var preferences = mock(Preferences.class);
        when(prefs.resolve(any())).thenReturn(preferences);
        when(preferences.getOrDefault(any())).thenAnswer(inv -> {
            var key = inv.getArgument(0, io.casehub.platform.api.preferences.PreferenceKey.class);
            return key.defaultValue();
        });
        service = new MergeQueueService();
        setField(service, "store", store);
        setField(service, "preferenceProvider", prefs);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private BatchRecord batch(String repo, boolean succeeded) {
        return new BatchRecord("b-" + UUID.randomUUID(), UUID.randomUUID(),
                List.of(1), repo, Instant.now().minusSeconds(300), Instant.now(), succeeded);
    }

    @Test
    void emptyCompletedBatches_returnsEmptyList() {
        when(store.completedBatchesSince(any())).thenReturn(List.of());
        assertThat(service.failureRateByRepository()).isEmpty();
    }

    @Test
    void singleRepo_computesRate() {
        when(store.completedBatchesSince(any())).thenReturn(List.of(
                batch("casehubio/devtown", true),
                batch("casehubio/devtown", false),
                batch("casehubio/devtown", true),
                batch("casehubio/devtown", false)
        ));
        var rates = service.failureRateByRepository();
        assertThat(rates).hasSize(1);
        assertThat(rates.get(0).repository()).isEqualTo("casehubio/devtown");
        assertThat(rates.get(0).total()).isEqualTo(4);
        assertThat(rates.get(0).failed()).isEqualTo(2);
        assertThat(rates.get(0).failureRate()).isEqualTo(0.5);
    }

    @Test
    void multipleRepos_sortedByFailureRateDescending() {
        when(store.completedBatchesSince(any())).thenReturn(List.of(
                batch("repo-a", true),
                batch("repo-a", true),
                batch("repo-b", false),
                batch("repo-b", false),
                batch("repo-b", true)
        ));
        var rates = service.failureRateByRepository();
        assertThat(rates).hasSize(2);
        assertThat(rates.get(0).repository()).isEqualTo("repo-b");
        assertThat(rates.get(0).failureRate()).isCloseTo(0.667, org.assertj.core.data.Offset.offset(0.01));
        assertThat(rates.get(1).repository()).isEqualTo("repo-a");
        assertThat(rates.get(1).failureRate()).isEqualTo(0.0);
    }

    @Test
    void allSuccessful_zeroRate() {
        when(store.completedBatchesSince(any())).thenReturn(List.of(
                batch("repo-a", true),
                batch("repo-a", true)
        ));
        var rates = service.failureRateByRepository();
        assertThat(rates.get(0).failureRate()).isEqualTo(0.0);
    }

    @Test
    void alertEvaluation_aboveThresholdAndMinBatches_fires() {
        when(store.completedBatchesSince(any())).thenReturn(List.of(
                batch("repo-a", false), batch("repo-a", false),
                batch("repo-a", false), batch("repo-a", false),
                batch("repo-a", false), batch("repo-a", true),
                batch("repo-a", true), batch("repo-a", true),
                batch("repo-a", true), batch("repo-a", true)
                                                                   ));
        var alerts = service.evaluateFailureRateAlerts();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).repository()).isEqualTo("repo-a");
        assertThat(alerts.get(0).failureRate()).isEqualTo(0.5);
    }

    @Test
    void alertEvaluation_belowMinBatches_noAlert() {
        when(store.completedBatchesSince(any())).thenReturn(List.of(
                batch("repo-a", false), batch("repo-a", false),
                batch("repo-a", false)
                                                                   ));
        var alerts = service.evaluateFailureRateAlerts();
        assertThat(alerts).isEmpty();
    }

    @Test
    void alertEvaluation_belowThreshold_noAlert() {
        when(store.completedBatchesSince(any())).thenReturn(List.of(
                batch("repo-a", false), batch("repo-a", true),
                batch("repo-a", true), batch("repo-a", true),
                batch("repo-a", true), batch("repo-a", true),
                batch("repo-a", true), batch("repo-a", true),
                batch("repo-a", true), batch("repo-a", true)
                                                                   ));
        var alerts = service.evaluateFailureRateAlerts();
        assertThat(alerts).isEmpty();
    }
}
