package io.casehub.devtown.github;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

public final class GitHubAppJwtGenerator {

    private GitHubAppJwtGenerator() {}

    public static String generateJwt(String appId, RSAPrivateKey privateKey) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(appId)
                .issueTime(Date.from(now.minusSeconds(60)))
                .expirationTime(Date.from(now.plusSeconds(600)))
                .build();

            SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate GitHub App JWT", e);
        }
    }

    public static RSAPrivateKey loadPrivateKey(String pemFilePath) {
        Path path = Path.of(pemFilePath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException(
                "Private key file not found: " + pemFilePath);
        }
        try {
            String pem = Files.readString(path);
            boolean isPkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");

            String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

            byte[] decoded = Base64.getDecoder().decode(base64);
            KeyFactory kf = KeyFactory.getInstance("RSA");

            if (isPkcs1) {
                return (RSAPrivateKey) kf.generatePrivate(parsePkcs1(decoded));
            }
            return (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (IOException e) {
            throw new IllegalArgumentException(
                "Failed to read private key file: " + pemFilePath, e);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Failed to parse private key from: " + pemFilePath, e);
        }
    }

    private static RSAPrivateCrtKeySpec parsePkcs1(byte[] pkcs1Bytes) throws Exception {
        DerParser parser = new DerParser(pkcs1Bytes);
        parser.readSequenceStart();
        parser.readInteger(); // version
        BigInteger modulus = parser.readInteger();
        BigInteger publicExponent = parser.readInteger();
        BigInteger privateExponent = parser.readInteger();
        BigInteger primeP = parser.readInteger();
        BigInteger primeQ = parser.readInteger();
        BigInteger primeExponentP = parser.readInteger();
        BigInteger primeExponentQ = parser.readInteger();
        BigInteger crtCoefficient = parser.readInteger();
        return new RSAPrivateCrtKeySpec(modulus, publicExponent, privateExponent,
            primeP, primeQ, primeExponentP, primeExponentQ, crtCoefficient);
    }

    private static final class DerParser {
        private final byte[] data;
        private int pos;

        DerParser(byte[] data) { this.data = data; }

        void readSequenceStart() {
            if (data[pos++] != 0x30) throw new IllegalArgumentException("Expected SEQUENCE");
            readLength();
        }

        BigInteger readInteger() {
            if (data[pos++] != 0x02) throw new IllegalArgumentException("Expected INTEGER");
            int len = readLength();
            byte[] value = new byte[len];
            System.arraycopy(data, pos, value, 0, len);
            pos += len;
            return new BigInteger(1, value);
        }

        private int readLength() {
            int b = data[pos++] & 0xFF;
            if (b < 0x80) return b;
            int numBytes = b & 0x7F;
            int length = 0;
            for (int i = 0; i < numBytes; i++) {
                length = (length << 8) | (data[pos++] & 0xFF);
            }
            return length;
        }
    }
}
