package io.casehub.devtown.domain;

public record IntakeClassification(
    IntakeLane lane,
    double trustScore,
    int observationCount,
    String classificationReason
) {}
