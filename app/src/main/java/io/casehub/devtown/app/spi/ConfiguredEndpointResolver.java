package io.casehub.devtown.app.spi;

import io.casehub.devtown.github.PublicEndpointResolver;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.Optional;

@ApplicationScoped
@DefaultBean
public class ConfiguredEndpointResolver implements PublicEndpointResolver {

    @ConfigProperty(name = "devtown.public-url", defaultValue = "")
    String publicUrl;

    @Override
    public Optional<URI> resolve() {
        if (publicUrl == null || publicUrl.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(URI.create(publicUrl));
    }
}
