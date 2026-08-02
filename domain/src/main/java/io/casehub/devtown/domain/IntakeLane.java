package io.casehub.devtown.domain;

public enum IntakeLane {
    FAST_TRACK(3),
    STANDARD(2),
    TRIAGE(1);

    private final int weight;
    IntakeLane(int weight) { this.weight = weight; }
    public int weight() { return weight; }
}
