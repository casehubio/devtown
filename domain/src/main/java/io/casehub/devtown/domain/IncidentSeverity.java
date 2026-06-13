package io.casehub.devtown.domain;

public enum IncidentSeverity {

    LOW(0.3),
    MEDIUM(0.5),
    HIGH(0.7),
    CRITICAL(0.9);

    private final double confidence;

    IncidentSeverity(double confidence) {
        this.confidence = confidence;
    }

    public double confidence() {
        return confidence;
    }
}
