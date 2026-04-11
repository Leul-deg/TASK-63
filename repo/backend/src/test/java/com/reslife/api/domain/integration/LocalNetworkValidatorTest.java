package com.reslife.api.domain.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalNetworkValidatorTest {

    private final LocalNetworkValidator validator = new LocalNetworkValidator();

    // ── Addresses that must pass ──────────────────────────────────────────

    @Test
    void loopback_passes() {
        assertDoesNotThrow(() -> validator.requireLocalTarget("http://127.0.0.1/hook"));
    }

    @Test
    void loopbackLocalhost_passes() {
        assertDoesNotThrow(() -> validator.requireLocalTarget("http://localhost/hook"));
    }

    @Test
    void privateClassC_passes() {
        assertDoesNotThrow(() -> validator.requireLocalTarget("http://192.168.1.100/hook"));
    }

    @Test
    void privateClassA_passes() {
        assertDoesNotThrow(() -> validator.requireLocalTarget("http://10.0.0.1/hook"));
    }

    @Test
    void linkLocal_passes() {
        assertDoesNotThrow(() -> validator.requireLocalTarget("http://169.254.1.1/hook"));
    }

    @Test
    void httpsScheme_passes() {
        assertDoesNotThrow(() -> validator.requireLocalTarget("https://192.168.0.5/hook"));
    }

    // ── Addresses that must be rejected ───────────────────────────────────

    @Test
    void publicIp_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.requireLocalTarget("http://8.8.8.8/hook"));
    }

    @Test
    void nullUrl_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.requireLocalTarget(null));
    }

    @Test
    void blankUrl_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.requireLocalTarget("   "));
    }

    @Test
    void ftpScheme_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.requireLocalTarget("ftp://192.168.1.1/file"));
    }

    @Test
    void fileScheme_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.requireLocalTarget("file:///etc/passwd"));
    }
}
