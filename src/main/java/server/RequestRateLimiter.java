package server;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

final class RequestRateLimiter {
    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, ArrayDeque<Long>> attempts = new ConcurrentHashMap<>();

    RequestRateLimiter(int limit, Duration window) {
        this(limit, window, Clock.systemUTC());
    }

    RequestRateLimiter(int limit, Duration window, Clock clock) {
        this.limit = Math.max(1, limit);
        this.windowMillis = Math.max(1, window.toMillis());
        this.clock = clock;
    }

    boolean tryAcquire(String key) {
        var attemptsForKey = attempts.computeIfAbsent(key == null ? "unknown" : key, ignored -> new ArrayDeque<>());
        var now = clock.millis();
        synchronized (attemptsForKey) {
            while (!attemptsForKey.isEmpty() && attemptsForKey.peekFirst() <= now - windowMillis) {
                attemptsForKey.removeFirst();
            }
            if (attemptsForKey.size() >= limit) {
                return false;
            }
            attemptsForKey.addLast(now);
            return true;
        }
    }
}
