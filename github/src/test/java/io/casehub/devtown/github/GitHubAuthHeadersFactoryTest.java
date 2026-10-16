package io.casehub.devtown.github;

import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubAuthHeadersFactoryTest {

    @Test
    void setsAuthorizationHeaderWhenTokenPresent() {
        var factory = new GitHubAuthHeadersFactory(new PatTokenProvider("ghp_test"));
        var incoming = new MultivaluedHashMap<String, String>();
        var outgoing = new MultivaluedHashMap<String, String>();

        factory.update(incoming, outgoing);

        assertThat(outgoing.getFirst("Authorization")).isEqualTo("Bearer ghp_test");
    }

    @Test
    void omitsAuthorizationHeaderWhenTokenEmpty() {
        var factory = new GitHubAuthHeadersFactory(new PatTokenProvider(""));
        var incoming = new MultivaluedHashMap<String, String>();
        var outgoing = new MultivaluedHashMap<String, String>();

        factory.update(incoming, outgoing);

        assertThat(outgoing.containsKey("Authorization")).isFalse();
    }

    @Test
    void setsAcceptAndApiVersionHeaders() {
        var factory = new GitHubAuthHeadersFactory(new PatTokenProvider("ghp_test"));
        var incoming = new MultivaluedHashMap<String, String>();
        var outgoing = new MultivaluedHashMap<String, String>();

        factory.update(incoming, outgoing);

        assertThat(outgoing.getFirst("Accept")).isEqualTo("application/vnd.github+json");
        assertThat(outgoing.getFirst("X-GitHub-Api-Version")).isEqualTo("2022-11-28");
    }
}
