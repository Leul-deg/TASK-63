package com.reslife.api.encryption;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Centralised AES-256-GCM field-level encryption service.
 *
 * <h3>Algorithm details</h3>
 * <ul>
 *   <li>AES-256-GCM (authenticated encryption — confidentiality + integrity).</li>
 *   <li>A fresh 12-byte random IV is generated for every {@link #encrypt} call.</li>
 *   <li>The stored format is {@code Base64(IV ‖ ciphertext ‖ 128-bit GCM auth tag)}.</li>
 * </ul>
 *
 * <h3>Key configuration</h3>
 * Set {@code app.encryption.key} to a Base64-encoded 32-byte value.
 * Override via the {@code RESLIFE_ENCRYPTION_KEY} environment variable in production:
 * <pre>
 *   export RESLIFE_ENCRYPTION_KEY=$(openssl rand -base64 32)
 * </pre>
 *
 * <h3>JPA converter access</h3>
 * JPA {@link jakarta.persistence.AttributeConverter} instances are not Spring-managed beans
 * by default. The static {@link #instance()} accessor gives converters access to the
 * fully-initialised service after Spring has injected the key.
 */
@Service
public class EncryptionService {

    private static final String ALGORITHM    = "AES/GCM/NoPadding";
    private static final int    IV_BYTES     = 12;   // 96-bit IV — NIST recommended for GCM
    private static final int    TAG_BITS     = 128;  // 16-byte authentication tag

    // Lazily populated by @PostConstruct — safe for tests that don't start a Spring context
    private static EncryptionService INSTANCE;

    private final SecretKeySpec secretKey;
    private final SecureRandom  random = new SecureRandom();

    public EncryptionService(
            @Value("${app.encryption.key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "app.encryption.key must decode to exactly 32 bytes for AES-256, got: "
                    + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    @PostConstruct
    void registerInstance() {
        INSTANCE = this;
    }

    /**
     * Returns the singleton instance for use by JPA converters.
     * Throws if called before Spring has initialised the application context.
     */
    public static EncryptionService instance() {
        if (INSTANCE == null) {
            throw new IllegalStateException(
                    "EncryptionService not yet initialised — Spring context may not be started");
        }
        return INSTANCE;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Encrypts {@code plaintext} with AES-256-GCM.
     *
     * @return Base64-encoded {@code IV ‖ ciphertext ‖ tag}, or {@code null} if input is null.
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));

            // GCM appends the auth tag to the ciphertext output automatically
            byte[] ciphertextWithTag = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[IV_BYTES + ciphertextWithTag.length];
            System.arraycopy(iv,              0, combined, 0,       IV_BYTES);
            System.arraycopy(ciphertextWithTag, 0, combined, IV_BYTES, ciphertextWithTag.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new EncryptionException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a value produced by {@link #encrypt}.
     *
     * @return plaintext string, or {@code null} if input is null.
     * @throws EncryptionException if the ciphertext has been tampered with (GCM auth failure)
     *                             or is malformed.
     */
    public String decrypt(String encrypted) {
        if (encrypted == null) return null;
        try {
            byte[] combined        = Base64.getDecoder().decode(encrypted);
            byte[] iv              = Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] ciphertextWithTag = Arrays.copyOfRange(combined, IV_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));

            return new String(cipher.doFinal(ciphertextWithTag), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new EncryptionException("Decryption failed — data may be corrupted or the key is wrong", e);
        }
    }
}
