package com.example.ratelimiter.service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the sliding window counter rate limiting algorithm, in-memory.
 * Weighs the previous window's count by how much of it still bleeds into the
 * current window, smoothing out the burst-at-boundary problem of a naive
 * fixed-window counter.
 */
public class SlidingWindowRateLimiter implements RateLimiterService {

    private final int limit;
    private final int windowSeconds;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(int limit, int windowSeconds) {
        this.limit = limit;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public boolean isAllowed(String key) {
        Window window = windows.computeIfAbsent(key, k -> new Window());
        synchronized (window) {
            long now = System.currentTimeMillis() / 1000;
            long windowId = now / windowSeconds;
            long elapsed = now - windowId * windowSeconds;

            if (window.windowId != windowId) {
                window.previousCount = (window.windowId == windowId - 1) ? window.currentCount : 0;
                window.currentCount = 0;
                window.windowId = windowId;
            }

            double weightedCount = window.previousCount * ((double) (windowSeconds - elapsed) / windowSeconds)
                    + window.currentCount;

            if (weightedCount + 1 > limit) {
                return false;
            }

            window.currentCount++;
            return true;
        }
    }

    private static class Window {
        long windowId = -1;
        int currentCount = 0;
        int previousCount = 0;
    }
}
