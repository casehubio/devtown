package io.casehub.devtown.review;

import java.util.List;

public sealed interface ReviewerOutcome
        permits ReviewerOutcome.Completed, ReviewerOutcome.Declined, ReviewerOutcome.Failed {

    record Completed(List<String> findings) implements ReviewerOutcome {}
    record Declined(String reason)          implements ReviewerOutcome {}
    record Failed(String reason)            implements ReviewerOutcome {}
}
