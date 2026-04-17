package com.reslife.api.encryption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link EncryptionService} — no Spring context required.
 * Verifies AES-256-GCM encrypt/decrypt correctness, IV randomness, null
 * passthrough, tamper detection, and key-size enforcement.
 */
class EncryptionServiceTest {

    // 32 zero-bytes, Base64-encoded (44 chars including one '=' pad)
    private static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private EncryptionService service;

    @BeforeEach
    void setUp() {
        service = new EncryptionService(TEST_KEY);
    }

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    void encryptDecrypt_roundTrip_forAsciiString() {
        String plaintext = "Hello, World!";
        assertThat(service.decrypt(service.encrypt(plaintext))).isEqualTo(plaintext);
    }

    @Test
    void encryptDecrypt_roundTrip_forIso8601DateString() {
        // Exercises the real LocalDateEncryptionConverter use-case
        String date = "1990-03-15";
        assertThat(service.decrypt(service.encrypt(date))).isEqualTo(date);
    }

    @Test
    void encryptDecrypt_roundTrip_forUnicodeString() {
        String text = "café résumé naïve";
        assertThat(service.decrypt(service.encrypt(text))).isEqualTo(text);
    }

    // ── Null passthrough ──────────────────────────────────────────────────────

    @Test
    void encrypt_returnsNull_whenInputIsNull() {
        assertThat(service.encrypt(null)).isNull();
    }

    @Test
    void decrypt_returnsNull_whenInputIsNull() {
        assertThat(service.decrypt(null)).isNull();
    }

    // ── IV randomness ─────────────────────────────────────────────────────────

    @Test
    void encrypt_producesDifferentCiphertexts_forSamePlaintext() {
        // Each call generates a fresh random IV — two encryptions must differ
        String plaintext = "same input every time";
        assertThat(service.encrypt(plaintext)).isNotEqualTo(service.encrypt(plaintext));
    }

    // ── Tamper detection ─────────────────────────────────────────────────────

    @Test
    void decrypt_throwsEncryptionException_whenGcmTagIsTampered() {
        String encrypted = service.encrypt("sensitive data");
        byte[] raw = Base64.getDecoder().decode(encrypted);
        raw[raw.length - 1] ^= 0xFF; // flip the last byte of the GCM auth tag
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThrows(EncryptionException.class, () -> service.decrypt(tampered));
    }

    @Test
    void decrypt_throwsEncryptionException_whenCiphertextBodyIsCorrupted() {
        String encrypted = service.encrypt("sensitive data");
        byte[] raw = Base64.getDecoder().decode(encrypted);
        // Flip a byte in the ciphertext body (after the 12-byte IV)
        raw[12] ^= 0xFF;
        String corrupted = Base64.getEncoder().encodeToString(raw);

        assertThrows(EncryptionException.class, () -> service.decrypt(corrupted));
    }

    // ── Key validation ────────────────────────────────────────────────────────

    @Test
    void constructor_throwsIllegalArgumentException_whenKeyDecodesTo16Bytes() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalArgumentException.class, () -> new EncryptionService(shortKey));
    }

    @Test
    void constructor_throwsIllegalArgumentException_whenKeyDecodesTo64Bytes() {
        String longKey = Base64.getEncoder().encodeToString(new byte[64]);
        assertThrows(IllegalArgumentException.class, () -> new EncryptionService(longKey));
    }
}
