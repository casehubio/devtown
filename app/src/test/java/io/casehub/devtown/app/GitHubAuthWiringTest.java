package io.casehub.devtown.app;

import io.casehub.devtown.github.GitHubAuthHeadersFactory;
import io.casehub.devtown.github.GitHubTokenProvider;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class GitHubAuthWiringTest {

    @Inject
    GitHubTokenProvider tokenProvider;

    @Inject
    GitHubAuthHeadersFactory headersFactory;

    @Test
    void producerCreatesTokenProviderViaCdi() {
        assertThat(tokenProvider).isNotNull();
        assertThat(tokenProvider.getToken()).isNotNull();
    }

    @Test
    void headersFactoryIsWiredToProducedProvider() {
        var incoming = new MultivaluedHashMap<String, String>();
        var outgoing = new MultivaluedHashMap<String, String>();

        headersFactory.update(incoming, outgoing);

        assertThat(outgoing.getFirst("Accept")).isEqualTo("application/vnd.github+json");
        assertThat(outgoing.getFirst("X-GitHub-Api-Version")).isEqualTo("2022-11-28");
    }

    @Test
    void headersFactoryInjectsTokenFromProducer() {
        var incoming = new MultivaluedHashMap<String, String>();
        var outgoing = new MultivaluedHashMap<String, String>();

        headersFactory.update(incoming, outgoing);

        String authHeader = outgoing.getFirst("Authorization");
        String token = tokenProvider.getToken();
        if (token != null && !token.isEmpty()) {
            assertThat(authHeader).isEqualTo("Bearer " + token);
        }
    }
}
