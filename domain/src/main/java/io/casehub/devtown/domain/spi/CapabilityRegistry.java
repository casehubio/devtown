package io.casehub.devtown.domain.spi;

import io.casehub.devtown.domain.RoutingPolicy;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public interface CapabilityRegistry {

    Set<String> capabilities();

    Optional<RoutingPolicy> policy(String capability);

    default boolean isKnown(String capability) {
        Objects.requireNonNull(capability, "capability must not be null");
        return capabilities().contains(capability);
    }
}
