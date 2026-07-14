package io.casehub.devtown.app;

public record FailureRateAlertEvent(
        String repository,
        int totalBatches,
        int failedBatches,
        double failureRate,
        double threshold
) {}
