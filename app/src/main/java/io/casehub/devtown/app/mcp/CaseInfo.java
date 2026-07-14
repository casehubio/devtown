package io.casehub.devtown.app.mcp;

import io.casehub.devtown.review.PrPayload;
import java.time.Instant;
import java.util.UUID;

public record CaseInfo(
        UUID caseId,
        String tenancyId,
        PrPayload payload,
        Instant startedAt,
        Instant lastEventAt,
        CaseTrackingStatus status,
        UUID supersededBy,
        UUID supersedes
) {
    public CaseInfo(UUID caseId, String tenancyId, PrPayload payload,
                    Instant startedAt, Instant lastEventAt, CaseTrackingStatus status) {
        this(caseId, tenancyId, payload, startedAt, lastEventAt, status, null, null);
    }

    public CaseInfo withStatus(CaseTrackingStatus newStatus, Instant eventTime) {
        return new CaseInfo(caseId, tenancyId, payload, startedAt, eventTime, newStatus, supersededBy, supersedes);
    }

    public CaseInfo withHeadSha(String newSha) {
        var updatedPayload = new PrPayload(
                payload.repo(), payload.prNumber(), newSha,
                payload.baseRef(), payload.linesChanged(),
                payload.contributor(), payload.changedPaths()
        );
        return new CaseInfo(caseId, tenancyId, updatedPayload, startedAt, lastEventAt, status, supersededBy, supersedes);
    }

    public CaseInfo withSupersededBy(UUID replacementCaseId) {
        return new CaseInfo(caseId, tenancyId, payload, startedAt, lastEventAt, status, replacementCaseId, supersedes);
    }

    public CaseInfo withSupersedes(UUID replacedCaseId) {
        return new CaseInfo(caseId, tenancyId, payload, startedAt, lastEventAt, status, supersededBy, replacedCaseId);
    }

    public boolean isStalled(long thresholdMinutes) {
        return !status.isTerminal()
               && Instant.now().isAfter(lastEventAt.plusSeconds(thresholdMinutes * 60));
    }
}
