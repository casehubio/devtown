package io.casehub.devtown.domain;

import java.time.Duration;

public record FailurePolicy(
    boolean scopeReductionAllowed,
    String reducedInputSchema,
    Duration humanEscalationSla
) {}
