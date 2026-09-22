package io.casehub.devtown.review;

import io.casehub.devtown.domain.ReviewFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewerOutcomeTest {

    @Test
    void completed_holdsFindings() {
        var f1      = new ReviewFinding(ReviewFinding.Severity.HIGH, "injection", "src/A.java", null, "finding-1", 0.9);
        var f2      = new ReviewFinding(ReviewFinding.Severity.LOW, "naming", "src/B.java", null, "finding-2", 0.5);
        var outcome = new ReviewerOutcome.Completed(List.of(f1, f2));
        assertThat(outcome.findings()).containsExactly(f1, f2);
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
        var             finding   = new ReviewFinding(ReviewFinding.Severity.LOW, "test", "src/X.java", null, "f1", 0.5);
        ReviewerOutcome completed = new ReviewerOutcome.Completed(List.of(finding));
        ReviewerOutcome declined  = new ReviewerOutcome.Declined("reason");
        ReviewerOutcome failed    = new ReviewerOutcome.Failed("crash");

        String c = switch (completed) {
            case ReviewerOutcome.Completed x -> "completed:" + x.findings().size();
            case ReviewerOutcome.Declined x -> "declined";
            case ReviewerOutcome.Failed x -> "failed";
        };
        String d = switch (declined) {
            case ReviewerOutcome.Completed x -> "completed";
            case ReviewerOutcome.Declined x -> "declined:" + x.reason();
            case ReviewerOutcome.Failed x -> "failed";
        };
        String f = switch (failed) {
            case ReviewerOutcome.Completed x -> "completed";
            case ReviewerOutcome.Declined x -> "declined";
            case ReviewerOutcome.Failed x -> "failed:" + x.reason();
        };

        assertThat(c).isEqualTo("completed:1");
        assertThat(d).isEqualTo("declined:reason");
        assertThat(f).isEqualTo("failed:crash");
    }
}
