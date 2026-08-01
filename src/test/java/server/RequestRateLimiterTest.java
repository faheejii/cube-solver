package server;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestRateLimiterTest {
    @Test
    void tryAcquire_shouldLimitEachClientIndependently() {
        var limiter = new RequestRateLimiter(
                2,
                Duration.ofMinutes(1),
                Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC)
        );

        assertTrue(limiter.tryAcquire("client-a"));
        assertTrue(limiter.tryAcquire("client-a"));
        assertFalse(limiter.tryAcquire("client-a"));
        assertTrue(limiter.tryAcquire("client-b"));
    }
}
