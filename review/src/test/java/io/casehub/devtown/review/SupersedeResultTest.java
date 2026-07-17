package io.casehub.devtown.review;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SupersedeResultTest {

    @Test
    void succeeded_trueWhenBothCaseIdsPresent() {
        var result = new SupersedeResult(
            UUID.randomUUID(), UUID.randomUUID(),
            new PrReviewOutcome("APPROVED", List.of(), null)
        );
        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void noActiveCase_hasNullIds_notSucceeded() {
        var result = SupersedeResult.noActiveCase();
        assertThat(result.succeeded()).isFalse();
        assertThat(result.supersededCaseId()).isNull();
        assertThat(result.replacementCaseId()).isNull();
        assertThat(result.reviewOutcome()).isNull();
    }

    @Test
    void alreadyTerminal_hasOldIdOnly_notSucceeded() {
        UUID oldId = UUID.randomUUID();
        var result = SupersedeResult.alreadyTerminal(oldId);
        assertThat(result.succeeded()).isFalse();
        assertThat(result.supersededCaseId()).isEqualTo(oldId);
        assertThat(result.replacementCaseId()).isNull();
    }
}
