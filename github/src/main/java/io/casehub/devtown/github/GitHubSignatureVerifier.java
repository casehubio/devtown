package io.casehub.devtown.github;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class GitHubSignatureVerifier {

    private static final String PREFIX = "sha256=";

    private GitHubSignatureVerifier() {}

    public static boolean verify(String body, String signatureHeader, String secret) {
        if (signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            return false;
        }
        String receivedHex = signatureHeader.substring(PREFIX.length());
        byte[] received;
        try {
            received = HexFormat.of().parseHex(receivedHex);
        } catch (IllegalArgumentException e) {
            return false;
        }

        byte[] expected = computeHmac(body, secret);
        return MessageDigest.isEqual(expected, received);
    }

    private static byte[] computeHmac(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
