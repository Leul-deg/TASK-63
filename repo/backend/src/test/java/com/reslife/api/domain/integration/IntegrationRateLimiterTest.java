package com.reslife.api.domain.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationRateLimiterTest {

    private final IntegrationRateLimiter limiter = new IntegrationRateLimiter();

    @Test
    void firstRequest_isAccepted() {
        assertTrue(limiter.tryAcquire("key-1"));
    }

    @Test
    void limitRequests_allAccepted() {
        for (int i = 0; i < IntegrationRateLimiter.LIMIT; i++) {
            assertTrue(limiter.tryAcquire("key-fill"), "request " + i + " should be accepted");
        }
    }

    @Test
    void limitPlusOne_isRejected() {
        for (int i = 0; i < IntegrationRateLimiter.LIMIT; i++) {
            limiter.tryAcquire("key-over");
        }
        assertFalse(limiter.tryAcquire("key-over"), "61st request must be rejected");
    }

    @Test
    void differentKeys_trackedIndependently() {
        for (int i = 0; i < IntegrationRateLimiter.LIMIT; i++) {
            limiter.tryAcquire("key-a");
        }
        // key-b window is clean
        assertTrue(limiter.tryAcquire("key-b"));
        // key-a is exhausted
        assertFalse(limiter.tryAcquire("key-a"));
    }

    @Test
    void currentCount_reflectsConsumedSlots() {
        assertEquals(0, limiter.currentCount("key-count"));
        limiter.tryAcquire("key-count");
        limiter.tryAcquire("key-count");
        limiter.tryAcquire("key-count");
        assertEquals(3, limiter.currentCount("key-count"));
    }

    @Test
    void currentCount_unknownKey_returnsZero() {
        assertEquals(0, limiter.currentCount("key-never-used"));
    }
}
