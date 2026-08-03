package io.casehub.devtown.review;

public record PrClosePayload(
        String repo,
        int prNumber,
        boolean merged,
        String contributorLogin,
        long contributorNumericId,
        String senderLogin,
        long senderNumericId,
        String senderType
) {}
