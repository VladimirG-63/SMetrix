package ru.smetrix.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/** Неблокирующий локальный rate limiter для публичных auth endpoints. */
@Component
public class AuthRateLimiter {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int maxAttempts, Duration duration) {
        long now = System.currentTimeMillis();
        long durationMs = duration.toMillis();
        Window result = windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt >= durationMs) {
                return new Window(now, 1);
            }
            return new Window(current.startedAt, current.attempts + 1);
        });
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= durationMs);
        }
        return result.attempts <= maxAttempts;
    }

    private static final class Window {
        private final long startedAt;
        private final int attempts;

        private Window(long startedAt, int attempts) {
            this.startedAt = startedAt;
            this.attempts = attempts;
        }
    }
}
