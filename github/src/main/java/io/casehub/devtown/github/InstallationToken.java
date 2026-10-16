package io.casehub.devtown.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record InstallationToken(
    @JsonProperty("token") String token,
    @JsonProperty("expires_at") Instant expiresAt) {}
