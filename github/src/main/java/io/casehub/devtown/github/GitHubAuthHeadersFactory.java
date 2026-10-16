package io.casehub.devtown.github;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

@ApplicationScoped
public class GitHubAuthHeadersFactory implements ClientHeadersFactory {

    private final GitHubTokenProvider tokenProvider;

    @Inject
    public GitHubAuthHeadersFactory(GitHubTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public MultivaluedMap<String, String> update(
            MultivaluedMap<String, String> incomingHeaders,
            MultivaluedMap<String, String> clientOutgoingHeaders) {
        String token = tokenProvider.getToken();
        if (token != null && !token.isEmpty()) {
            clientOutgoingHeaders.putSingle("Authorization", "Bearer " + token);
        }
        clientOutgoingHeaders.putSingle("Accept", "application/vnd.github+json");
        clientOutgoingHeaders.putSingle("X-GitHub-Api-Version", "2022-11-28");
        return clientOutgoingHeaders;
    }
}
