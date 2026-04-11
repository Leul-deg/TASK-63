package com.reslife.api.domain.integration;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * HMAC-SHA256 signing and verification for the integration layer.
 *
 * <h3>Signing string</h3>
 * <pre>
 *   {epochSeconds}\n{requestBody}
 * </pre>
 *
 * <h3>Signature header</h3>
 * <pre>
 *   X-Reslife-Signature: sha256={lowercaseHex}
 * </pre>
 *
 * <h3>Replay protection</h3>
 * <p>Verification rejects timestamps more than {@value #MAX_SKEW_SECONDS} seconds
 * from the server clock in either direction.
 */
@Service
public class HmacService {

    static final String ALGORITHM        = "HmacSHA256";
    static final long   MAX_SKEW_SECONDS = 300L; // 5 minutes

    /**
     * Signs the payload and returns the full signature string (e.g. {@code sha256=abc123...}).
     *
     * @param secret           plaintext HMAC secret
     * @param timestampSeconds Unix epoch seconds to embed in the signing string
     * @param body             raw request/response body bytes
     */
    public String sign(String secret, long timestampSeconds, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            mac.update((timestampSeconds + "\n").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }

    /**
     * Verifies an inbound request's timestamp and signature.
     *
     * @throws InvalidSignatureException if the timestamp is stale or the signature doesn't match
     */
    public void verify(String secret, long timestampSeconds, byte[] body, String providedSignature) {
        long skew = Math.abs(Instant.now().getEpochSecond() - timestampSeconds);
        if (skew > MAX_SKEW_SECONDS) {
            throw new InvalidSignatureException(
                    "Timestamp is " + skew + "s from server clock — max allowed is " + MAX_SKEW_SECONDS + "s");
        }
        String expected = sign(secret, timestampSeconds, body);
        // Constant-time comparison prevents timing-oracle attacks
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidSignatureException("Signature mismatch");
        }
    }
}
