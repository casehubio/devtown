package io.casehub.devtown.app.mcp;

import io.casehub.devtown.review.PrPayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CaseInfoTest {

    private final UUID caseId = UUID.randomUUID();
    private final PrPayload payload = new PrPayload("repo", 1, "sha", "main", 10, "alice", List.of());
    private final Instant now = Instant.now();

    @Test
    void defaultConstruction_hasNullSupersedeLinks() {
        var info = new CaseInfo(caseId, "t", payload, now, now, CaseTrackingStatus.RUNNING);
        assertThat(info.supersededBy()).isNull();
        assertThat(info.supersedes()).isNull();
    }

    @Test
    void withSupersededBy_setsLink_preservesOtherFields() {
        var info = new CaseInfo(caseId, "t", payload, now, now, CaseTrackingStatus.RUNNING);
        UUID replacementId = UUID.randomUUID();

        CaseInfo updated = info.withSupersededBy(replacementId);

        assertThat(updated.supersededBy()).isEqualTo(replacementId);
        assertThat(updated.supersedes()).isNull();
        assertThat(updated.caseId()).isEqualTo(caseId);
        assertThat(updated.status()).isEqualTo(CaseTrackingStatus.RUNNING);
    }

    @Test
    void withSupersedes_setsLink_preservesOtherFields() {
        var info = new CaseInfo(caseId, "t", payload, now, now, CaseTrackingStatus.RUNNING);
        UUID replacedId = UUID.randomUUID();

        CaseInfo updated = info.withSupersedes(replacedId);

        assertThat(updated.supersedes()).isEqualTo(replacedId);
        assertThat(updated.supersededBy()).isNull();
        assertThat(updated.caseId()).isEqualTo(caseId);
    }

    @Test
    void withStatus_preservesSupersedeLinks() {
        UUID replacementId = UUID.randomUUID();
        var info = new CaseInfo(caseId, "t", payload, now, now, CaseTrackingStatus.RUNNING, replacementId, null);

        CaseInfo updated = info.withStatus(CaseTrackingStatus.SUPERSEDED, now);

        assertThat(updated.status()).isEqualTo(CaseTrackingStatus.SUPERSEDED);
        assertThat(updated.supersededBy()).isEqualTo(replacementId);
    }

    @Test
    void withHeadSha_preservesSupersedeLinks() {
        UUID replacedId = UUID.randomUUID();
        var info = new CaseInfo(caseId, "t", payload, now, now, CaseTrackingStatus.RUNNING, null, replacedId);

        CaseInfo updated = info.withHeadSha("newsha");

        assertThat(updated.payload().headSha()).isEqualTo("newsha");
        assertThat(updated.supersedes()).isEqualTo(replacedId);
    }
}
