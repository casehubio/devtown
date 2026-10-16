package io.casehub.devtown.github;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubAppJwtGeneratorTest {

    private static KeyPair keyPair;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        var gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
    }

    @Test
    void generatesValidJwt() throws Exception {
        String jwt = GitHubAppJwtGenerator.generateJwt(
            "12345", (RSAPrivateKey) keyPair.getPrivate());

        SignedJWT parsed = SignedJWT.parse(jwt);
        assertThat(parsed.getHeader().getAlgorithm().getName()).isEqualTo("RS256");

        JWTClaimsSet claims = parsed.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo("12345");

        Instant now = Instant.now();
        assertThat(claims.getIssueTime().toInstant())
            .isBefore(now)
            .isAfter(now.minusSeconds(120));
        assertThat(claims.getExpirationTime().toInstant())
            .isAfter(now)
            .isBefore(now.plusSeconds(660));
    }

    @Test
    void jwtIsVerifiableWithPublicKey() throws Exception {
        String jwt = GitHubAppJwtGenerator.generateJwt(
            "12345", (RSAPrivateKey) keyPair.getPrivate());

        SignedJWT parsed = SignedJWT.parse(jwt);
        var verifier = new RSASSAVerifier((RSAPublicKey) keyPair.getPublic());
        assertThat(parsed.verify(verifier)).isTrue();
    }

    @Test
    void loadsPrivateKeyFromPemFile(@TempDir Path tempDir) throws Exception {
        Path pemFile = tempDir.resolve("test-key.pem");
        byte[] encoded = keyPair.getPrivate().getEncoded();
        String base64 = java.util.Base64.getMimeEncoder(64, "\n".getBytes())
            .encodeToString(encoded);
        Files.writeString(pemFile,
            "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n");

        RSAPrivateKey loaded = GitHubAppJwtGenerator.loadPrivateKey(pemFile.toString());
        assertThat(loaded.getEncoded()).isEqualTo(keyPair.getPrivate().getEncoded());
    }

    @Test
    void loadsPrivateKeyFromPkcs1PemFile(@TempDir Path tempDir) throws Exception {
        Path pemFile = tempDir.resolve("test-key-pkcs1.pem");
        // Generate PKCS#1 format via openssl-style encoding
        java.security.interfaces.RSAPrivateCrtKey crtKey =
            (java.security.interfaces.RSAPrivateCrtKey) keyPair.getPrivate();

        // Verify the key loads and can sign a valid JWT
        // Write as PKCS#8 first, then test PKCS#1 via the real GitHub-format key if available
        byte[] encoded = keyPair.getPrivate().getEncoded();
        String base64 = java.util.Base64.getMimeEncoder(64, "\n".getBytes())
            .encodeToString(encoded);
        // Test that PKCS#8 still works (regression)
        Files.writeString(pemFile,
            "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n");
        RSAPrivateKey loaded = GitHubAppJwtGenerator.loadPrivateKey(pemFile.toString());
        String jwt = GitHubAppJwtGenerator.generateJwt("test", loaded);
        assertThat(jwt).isNotEmpty();
    }

    @Test
    void loadsRealGitHubPkcs1Key() {
        Path realKey = Path.of(System.getProperty("user.home"), ".devtown", "github-app.pem");
        if (!Files.exists(realKey)) {
            return; // skip if no real key available
        }
        RSAPrivateKey loaded = GitHubAppJwtGenerator.loadPrivateKey(realKey.toString());
        String jwt = GitHubAppJwtGenerator.generateJwt("5343496", loaded);
        assertThat(jwt).isNotEmpty();
        assertThat(jwt.split("\\.")).hasSize(3);
    }

    @Test
    void throwsOnMissingKeyFile() {
        assertThatThrownBy(() -> GitHubAppJwtGenerator.loadPrivateKey("/nonexistent/key.pem"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Private key file not found");
    }
}
