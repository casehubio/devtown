package io.casehub.devtown.github;

import java.net.URI;
import java.util.Optional;

public interface PublicEndpointResolver {
    Optional<URI> resolve();
}
