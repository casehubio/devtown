package io.casehub.devtown.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AppTokenProviderTest {

    private static final String APP_ID = "12345";
    private static final String INSTALLATION_ID = "67890";

    @TempDir
    Path tempDir;
    private String keyPath;

    @BeforeEach
    void setUp() throws Exception {
        var gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        var keyPair = gen.generateKeyPair();

        Path pemFile = tempDir.resolve("test-key.pem");
        byte[] encoded = keyPair.getPrivate().getEncoded();
        String base64 = java.util.Base64.getMimeEncoder(64, "\n".getBytes())
            .encodeToString(encoded);
        Files.writeString(pemFile,
            "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n");
        keyPath = pemFile.toString();
    }

    @Test
    void fetchesTokenOnFirstCall() {
        var callCount = new AtomicInteger(0);
        InstallationTokenClient mockClient = (id, jwt) -> {
            callCount.incrementAndGet();
            return new InstallationToken("ghs_fresh",
                Instant.now().plusSeconds(3600));
        };

        var provider = new AppTokenProvider(APP_ID, keyPath, INSTALLATION_ID, mockClient);
        String token = provider.getToken();

        assertThat(token).isEqualTo("ghs_fresh");
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void returnsCachedTokenOnSubsequentCalls() {
        var callCount = new AtomicInteger(0);
        InstallationTokenClient mockClient = (id, jwt) -> {
            callCount.incrementAndGet();
            return new InstallationToken("ghs_cached",
                Instant.now().plusSeconds(3600));
        };

        var provider = new AppTokenProvider(APP_ID, keyPath, INSTALLATION_ID, mockClient);
        provider.getToken();
        provider.getToken();
        provider.getToken();

        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void refreshesExpiringSoonToken() {
        var callCount = new AtomicInteger(0);
        InstallationTokenClient mockClient = (id, jwt) -> {
            int call = callCount.incrementAndGet();
            if (call == 1) {
                return new InstallationToken("ghs_expiring",
                    Instant.now().plusSeconds(60));
            }
            return new InstallationToken("ghs_refreshed",
                Instant.now().plusSeconds(3600));
        };

        var provider = new AppTokenProvider(APP_ID, keyPath, INSTALLATION_ID, mockClient);
        provider.getToken();
        String refreshed = provider.getToken();

        assertThat(refreshed).isEqualTo("ghs_refreshed");
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void passesCorrectInstallationId() {
        var capturedId = new String[1];
        InstallationTokenClient mockClient = (id, jwt) -> {
            capturedId[0] = id;
            return new InstallationToken("ghs_test",
                Instant.now().plusSeconds(3600));
        };

        var provider = new AppTokenProvider(APP_ID, keyPath, INSTALLATION_ID, mockClient);
        provider.getToken();

        assertThat(capturedId[0]).isEqualTo(INSTALLATION_ID);
    }

    @Test
    void passesJwtAuthHeader() {
        var capturedAuth = new String[1];
        InstallationTokenClient mockClient = (id, jwt) -> {
            capturedAuth[0] = jwt;
            return new InstallationToken("ghs_test",
                Instant.now().plusSeconds(3600));
        };

        var provider = new AppTokenProvider(APP_ID, keyPath, INSTALLATION_ID, mockClient);
        provider.getToken();

        assertThat(capturedAuth[0]).startsWith("Bearer ");
    }
}
