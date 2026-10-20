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

    @ConfigProperty(name = "devtown.public-url")
    Optional<String> publicUrl;

    @Override
    public Optional<URI> resolve() {
        return publicUrl.filter(url -> !url.isBlank()).map(URI::create);
    }
}
