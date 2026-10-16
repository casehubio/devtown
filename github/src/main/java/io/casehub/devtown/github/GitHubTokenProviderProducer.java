package io.casehub.devtown.github;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Optional;

@ApplicationScoped
public class GitHubTokenProviderProducer {

    @Produces
    @ApplicationScoped
    public GitHubTokenProvider tokenProvider(
            @ConfigProperty(name = "devtown.github.app.id") Optional<String> appId,
            @ConfigProperty(name = "devtown.github.app.private-key-path") Optional<String> keyPath,
            @ConfigProperty(name = "devtown.github.app.installation-id") Optional<String> installationId,
            @ConfigProperty(name = "devtown.github.token") Optional<String> pat,
            @RestClient InstallationTokenClient tokenClient) {

        boolean hasAppId = appId.filter(s -> !s.isEmpty()).isPresent();
        boolean hasKeyPath = keyPath.filter(s -> !s.isEmpty()).isPresent();
        boolean hasInstId = installationId.filter(s -> !s.isEmpty()).isPresent();

        if (hasAppId && hasKeyPath && hasInstId) {
            return new AppTokenProvider(appId.get(), keyPath.get(),
                installationId.get(), tokenClient);
        }
        if (hasAppId || hasKeyPath || hasInstId) {
            throw new IllegalStateException(
                "Partial GitHub App config: all three of " +
                "devtown.github.app.id, devtown.github.app.private-key-path, " +
                "devtown.github.app.installation-id must be set, or none");
        }
        return new PatTokenProvider(pat.orElse(""));
    }
}
