package io.casehub.devtown.queue;

public enum PriorityLane {
    NORMAL(1), HIGH(2), CRITICAL(3);

    private final int weight;

    PriorityLane(int weight) { this.weight = weight; }

    public int weight() { return weight; }
}
