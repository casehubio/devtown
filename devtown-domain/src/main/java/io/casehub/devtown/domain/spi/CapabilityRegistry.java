package io.casehub.devtown.domain.spi;

import io.casehub.devtown.domain.RoutingPolicy;
import java.util.Optional;
import java.util.Set;

public interface CapabilityRegistry {

    Set<String> capabilities();

    Optional<RoutingPolicy> policy(String capability);

    boolean isKnown(String capability);
}
