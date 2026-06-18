package io.casehub.devtown.review;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ReviewerOutcomeTest {

    @Test
    void completed_holdsFindings() {
        var outcome = new ReviewerOutcome.Completed(List.of("finding-1", "finding-2"));
        assertThat(outcome.findings()).containsExactly("finding-1", "finding-2");
    }

    @Test
    void declined_holdsReason() {
        var outcome = new ReviewerOutcome.Declined("out of scope");
        assertThat(outcome.reason()).isEqualTo("out of scope");
    }

    @Test
    void failed_holdsReason() {
        var outcome = new ReviewerOutcome.Failed("agent process crashed");
        assertThat(outcome.reason()).isEqualTo("agent process crashed");
    }

    @Test
    void patternMatch_coversAllPermits() {
        ReviewerOutcome completed = new ReviewerOutcome.Completed(List.of("f1"));
        ReviewerOutcome declined  = new ReviewerOutcome.Declined("reason");
        ReviewerOutcome failed    = new ReviewerOutcome.Failed("crash");

        String c = switch (completed) {
            case ReviewerOutcome.Completed x -> "completed:" + x.findings().size();
            case ReviewerOutcome.Declined x  -> "declined";
            case ReviewerOutcome.Failed x    -> "failed";
        };
        String d = switch (declined) {
            case ReviewerOutcome.Completed x -> "completed";
            case ReviewerOutcome.Declined x  -> "declined:" + x.reason();
            case ReviewerOutcome.Failed x    -> "failed";
        };
        String f = switch (failed) {
            case ReviewerOutcome.Completed x -> "completed";
            case ReviewerOutcome.Declined x  -> "declined";
            case ReviewerOutcome.Failed x    -> "failed:" + x.reason();
        };

        assertThat(c).isEqualTo("completed:1");
        assertThat(d).isEqualTo("declined:reason");
        assertThat(f).isEqualTo("failed:crash");
    }
}
