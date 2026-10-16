package io.casehub.devtown.github;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubTokenProviderProducerTest {

    private static final InstallationTokenClient DUMMY_CLIENT = (id, jwt) -> null;

    @Test
    void createsPatProviderWhenNoAppConfig() {
        var producer = new GitHubTokenProviderProducer();

        GitHubTokenProvider provider = producer.tokenProvider(
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of("ghp_mytoken"), DUMMY_CLIENT);

        assertThat(provider).isInstanceOf(PatTokenProvider.class);
        assertThat(provider.getToken()).isEqualTo("ghp_mytoken");
    }

    @Test
    void createsPatProviderWithEmptyTokenWhenNothingConfigured() {
        var producer = new GitHubTokenProviderProducer();

        GitHubTokenProvider provider = producer.tokenProvider(
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), DUMMY_CLIENT);

        assertThat(provider).isInstanceOf(PatTokenProvider.class);
        assertThat(provider.getToken()).isEmpty();
    }

    @Test
    void treatsEmptyStringsAsAbsent() {
        var producer = new GitHubTokenProviderProducer();

        GitHubTokenProvider provider = producer.tokenProvider(
            Optional.of(""), Optional.of(""), Optional.of(""),
            Optional.of("ghp_pat"), DUMMY_CLIENT);

        assertThat(provider).isInstanceOf(PatTokenProvider.class);
        assertThat(provider.getToken()).isEqualTo("ghp_pat");
    }

    @Test
    void createsAppProviderWhenAllAppConfigPresent(@TempDir Path tempDir) throws Exception {
        var gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        var keyPair = gen.generateKeyPair();
        Path pemFile = tempDir.resolve("key.pem");
        byte[] encoded = keyPair.getPrivate().getEncoded();
        String base64 = java.util.Base64.getMimeEncoder(64, "\n".getBytes())
            .encodeToString(encoded);
        Files.writeString(pemFile,
            "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n");

        var producer = new GitHubTokenProviderProducer();
        InstallationTokenClient mockClient = (id, jwt) ->
            new InstallationToken("ghs_test", Instant.now().plusSeconds(3600));

        GitHubTokenProvider provider = producer.tokenProvider(
            Optional.of("12345"), Optional.of(pemFile.toString()),
            Optional.of("67890"), Optional.of(""), mockClient);

        assertThat(provider).isInstanceOf(AppTokenProvider.class);
    }

    @Test
    void throwsOnPartialAppConfig() {
        var producer = new GitHubTokenProviderProducer();

        assertThatThrownBy(() -> producer.tokenProvider(
            Optional.of("12345"), Optional.empty(), Optional.empty(),
            Optional.empty(), DUMMY_CLIENT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Partial GitHub App config");
    }

    @Test
    void throwsOnPartialAppConfigMissingKeyPath() {
        var producer = new GitHubTokenProviderProducer();

        assertThatThrownBy(() -> producer.tokenProvider(
            Optional.of("12345"), Optional.empty(), Optional.of("67890"),
            Optional.empty(), DUMMY_CLIENT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Partial GitHub App config");
    }
}
