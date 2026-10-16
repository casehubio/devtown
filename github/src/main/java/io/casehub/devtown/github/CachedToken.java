package io.casehub.devtown.github;

import java.time.Duration;
import java.time.Instant;

record CachedToken(String token, Instant expiresAt) {
    boolean isExpiringSoon(Instant now) {
        return now.isAfter(expiresAt.minus(Duration.ofMinutes(5)));
    }
}
