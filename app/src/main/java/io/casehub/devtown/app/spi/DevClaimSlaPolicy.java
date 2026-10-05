package io.casehub.devtown.app.spi;

import io.casehub.work.api.ClaimSlaContext;
import io.casehub.work.api.spi.ClaimSlaPolicy;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class DevClaimSlaPolicy implements ClaimSlaPolicy {

    @Override
    public String id() {
        return "continuation";
    }

    @Override
    public Instant computePoolDeadline(ClaimSlaContext context) {
        Duration remaining = context.totalPoolSlaDuration().minus(context.accumulatedUnclaimedDuration());
        return !remaining.isNegative() && !remaining.isZero() ? context.now().plus(remaining) : context.now();
    }
}
