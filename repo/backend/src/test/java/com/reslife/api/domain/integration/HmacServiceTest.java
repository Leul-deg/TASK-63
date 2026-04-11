package com.reslife.api.domain.integration;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class HmacServiceTest {

    private final HmacService hmac = new HmacService();
    private static final String SECRET = "test-secret-key";
    private static final byte[] BODY   = "hello world".getBytes();

    @Test
    void sign_producesExpectedFormat() {
        String sig = hmac.sign(SECRET, 1_000_000L, BODY);
        assertTrue(sig.startsWith("sha256="), "signature must start with sha256=");
        assertEquals(71, sig.length(), "sha256= (7) + 64 hex chars = 71");
    }

    @Test
    void sign_isDeterministic() {
        String sig1 = hmac.sign(SECRET, 1_000_000L, BODY);
        String sig2 = hmac.sign(SECRET, 1_000_000L, BODY);
        assertEquals(sig1, sig2);
    }

    @Test
    void sign_changeswithDifferentTimestamp() {
        String sig1 = hmac.sign(SECRET, 1_000_000L, BODY);
        String sig2 = hmac.sign(SECRET, 1_000_001L, BODY);
        assertNotEquals(sig1, sig2);
    }

    @Test
    void verify_succeedsWithMatchingSignature() {
        long now = Instant.now().getEpochSecond();
        String sig = hmac.sign(SECRET, now, BODY);
        assertDoesNotThrow(() -> hmac.verify(SECRET, now, BODY, sig));
    }

    @Test
    void verify_rejectsStaleTimestamp() {
        long stale = Instant.now().getEpochSecond() - HmacService.MAX_SKEW_SECONDS - 1;
        String sig = hmac.sign(SECRET, stale, BODY);
        assertThrows(InvalidSignatureException.class,
                () -> hmac.verify(SECRET, stale, BODY, sig));
    }

    @Test
    void verify_rejectsFutureTimestamp() {
        long future = Instant.now().getEpochSecond() + HmacService.MAX_SKEW_SECONDS + 1;
        String sig = hmac.sign(SECRET, future, BODY);
        assertThrows(InvalidSignatureException.class,
                () -> hmac.verify(SECRET, future, BODY, sig));
    }

    @Test
    void verify_rejectsWrongSignature() {
        long now = Instant.now().getEpochSecond();
        assertThrows(InvalidSignatureException.class,
                () -> hmac.verify(SECRET, now, BODY, "sha256=0000000000000000000000000000000000000000000000000000000000000000"));
    }

    @Test
    void verify_rejectsWrongSecret() {
        long now = Instant.now().getEpochSecond();
        String sig = hmac.sign("other-secret", now, BODY);
        assertThrows(InvalidSignatureException.class,
                () -> hmac.verify(SECRET, now, BODY, sig));
    }
}
