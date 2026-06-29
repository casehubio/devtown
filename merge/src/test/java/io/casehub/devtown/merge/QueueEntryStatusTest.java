package io.casehub.devtown.merge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueueEntryStatusTest {

    @Test
    void shouldHaveExpectedStatuses() {
        assertThat(QueueEntryStatus.values()).containsExactlyInAnyOrder(
            QueueEntryStatus.QUEUED,
            QueueEntryStatus.IN_BATCH,
            QueueEntryStatus.MERGED,
            QueueEntryStatus.REJECTED,
            QueueEntryStatus.DEQUEUED
        );
    }

    @Test
    void shouldConvertToAndFromString() {
        for (QueueEntryStatus status : QueueEntryStatus.values()) {
            String name = status.name();
            QueueEntryStatus parsed = QueueEntryStatus.valueOf(name);
            assertThat(parsed).isEqualTo(status);
        }
    }
}
