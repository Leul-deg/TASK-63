package com.reslife.api.domain.integration;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory sliding-window rate limiter for integration keys.
 *
 * <p>Allows up to {@value #LIMIT} requests per {@value #WINDOW_MS} ms per key.
 * Thread safety is achieved through per-key synchronization on the window deque.
 *
 * <p>Idle windows are evicted every 5 minutes to prevent unbounded memory growth.
 */
@Component
public class IntegrationRateLimiter {

    static final int  LIMIT     = 60;
    static final long WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<String, Deque<Long>> windows     = new ConcurrentHashMap<>();
    private final AtomicLong                             lastCleanup = new AtomicLong(System.currentTimeMillis());

    /**
     * Attempts to consume one request slot for the given key.
     *
     * @return {@code true} if the request is within the rate limit and was accepted;
     *         {@code false} if the key has exhausted its quota for this window
     */
    public boolean tryAcquire(String keyId) {
        maybeEvictIdleWindows();
        long now         = System.currentTimeMillis();
        long windowStart = now - WINDOW_MS;
        Deque<Long> deque = windows.computeIfAbsent(keyId, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < windowStart) {
                deque.pollFirst();
            }
            if (deque.size() >= LIMIT) return false;
            deque.addLast(now);
            return true;
        }
    }

    /** Returns the number of requests consumed in the current window for {@code keyId}. */
    public int currentCount(String keyId) {
        long windowStart = System.currentTimeMillis() - WINDOW_MS;
        Deque<Long> deque = windows.get(keyId);
        if (deque == null) return 0;
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < windowStart) {
                deque.pollFirst();
            }
            return deque.size();
        }
    }

    /** Removes windows that have been idle for at least one full window period. */
    private void maybeEvictIdleWindows() {
        long now = System.currentTimeMillis();
        long last = lastCleanup.get();
        if (now - last < 5 * 60_000L) return;
        if (!lastCleanup.compareAndSet(last, now)) return; // another thread beat us
        long windowStart = now - WINDOW_MS;
        windows.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                while (!e.getValue().isEmpty() && e.getValue().peekFirst() < windowStart) {
                    e.getValue().pollFirst();
                }
                return e.getValue().isEmpty();
            }
        });
    }
}
