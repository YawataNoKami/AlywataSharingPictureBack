package com.photoapp.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Simple in-memory sliding-window rate limiter keyed by an arbitrary string
 * (typically the client IP). Suitable for a single-instance deployment; it
 * intentionally avoids an external dependency such as bucket4j/Redis since
 * this application runs as a single backend instance for two users.
 *
 * <p>Not distributed: if the application is scaled horizontally, each
 * instance enforces its own independent limit.</p>
 */
@Component
public class InMemoryRateLimiter {

    private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /**
     * Records a hit for the given key and returns {@code true} if the
     * number of hits within the sliding window (now - windowSeconds, now]
     * is within the allowed limit, {@code false} otherwise.
     */
    public boolean tryAcquire(String key, int maxAttempts, int windowSeconds) {
        var now = Instant.now();
        var windowStart = now.minusSeconds(windowSeconds);
        var deque = hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(windowStart)) {
                deque.pollFirst();
            }
            if (deque.size() >= maxAttempts) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }
}
