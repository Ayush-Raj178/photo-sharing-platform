package com.photoshare.common;

import com.photoshare.config.PhotoShareProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AttemptLimiter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final long windowSeconds;

    @Autowired
    public AttemptLimiter(PhotoShareProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AttemptLimiter(PhotoShareProperties properties, Clock clock) {
        this.clock = clock;
        this.windowSeconds = properties.rateLimit().windowSeconds();
    }

    public void check(String key, int limit) {
        if (limit <= 0) {
            return;
        }
        Instant now = clock.instant();
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.expiresAt())) {
                return new Window(now.plusSeconds(windowSeconds), new AtomicInteger(1));
            }
            current.count().incrementAndGet();
            return current;
        });
        if (window.count().get() > limit) {
            throw ApiException.tooManyRequests("RATE_LIMITED", "Too many attempts; try again later");
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
        }
    }

    private record Window(Instant expiresAt, AtomicInteger count) {}
}
