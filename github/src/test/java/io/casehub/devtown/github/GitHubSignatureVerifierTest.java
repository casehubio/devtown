package io.casehub.devtown.github;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubSignatureVerifierTest {

    private static final String SECRET = "test-webhook-secret";
    private static final String BODY = "{\"action\":\"opened\",\"number\":42}";

    @Test
    void validSignature_returnsTrue() {
        String signature = "sha256=" + computeHmac(BODY, SECRET);
        assertThat(GitHubSignatureVerifier.verify(BODY, signature, SECRET)).isTrue();
    }

    @Test
    void invalidSignature_returnsFalse() {
        assertThat(GitHubSignatureVerifier.verify(BODY, "sha256=deadbeef", SECRET)).isFalse();
    }

    @Test
    void nullSignatureHeader_returnsFalse() {
        assertThat(GitHubSignatureVerifier.verify(BODY, null, SECRET)).isFalse();
    }

    @Test
    void emptySignatureHeader_returnsFalse() {
        assertThat(GitHubSignatureVerifier.verify(BODY, "", SECRET)).isFalse();
    }

    @Test
    void missingPrefix_returnsFalse() {
        String hmac = computeHmac(BODY, SECRET);
        assertThat(GitHubSignatureVerifier.verify(BODY, hmac, SECRET)).isFalse();
    }

    @Test
    void wrongPrefix_returnsFalse() {
        String hmac = computeHmac(BODY, SECRET);
        assertThat(GitHubSignatureVerifier.verify(BODY, "sha1=" + hmac, SECRET)).isFalse();
    }

    @Test
    void emptyBody_validSignature_returnsTrue() {
        String empty = "";
        String signature = "sha256=" + computeHmac(empty, SECRET);
        assertThat(GitHubSignatureVerifier.verify(empty, signature, SECRET)).isTrue();
    }

    private static String computeHmac(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }
}
