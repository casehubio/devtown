package io.casehub.devtown.domain.cbr;

public record ActivationThreshold(double minEvidence, double minFraction) {

    public static final ActivationThreshold DEFAULT = new ActivationThreshold(2.0, 0.4);
}
