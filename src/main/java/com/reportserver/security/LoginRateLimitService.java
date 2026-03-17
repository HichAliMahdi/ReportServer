package com.reportserver.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class LoginRateLimitService {

    @Value("${reportserver.security.rate-limit.login.enabled:true}")
    private boolean enabled;

    @Value("${reportserver.security.rate-limit.login.max-attempts:10}")
    private int maxAttempts;

    @Value("${reportserver.security.rate-limit.login.window-seconds:60}")
    private int windowSeconds;

    private final ConcurrentHashMap<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public boolean isAllowed(String key) {
        if (!enabled) {
            return true;
        }

        long now = Instant.now().toEpochMilli();
        long windowMs = windowSeconds * 1000L;

        Deque<Long> deque = attempts.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());

        synchronized (deque) {
            while (!deque.isEmpty() && (now - deque.peekFirst()) > windowMs) {
                deque.pollFirst();
            }

            if (deque.size() >= maxAttempts) {
                return false;
            }

            deque.addLast(now);
            return true;
        }
    }

    public long retryAfterSeconds(String key) {
        if (!enabled) {
            return 0;
        }

        Deque<Long> deque = attempts.get(key);
        if (deque == null || deque.isEmpty()) {
            return 0;
        }

        long now = Instant.now().toEpochMilli();
        long windowMs = windowSeconds * 1000L;
        long oldest = deque.peekFirst();
        if (oldest == 0) {
            return 0;
        }

        long remainingMs = windowMs - (now - oldest);
        if (remainingMs <= 0) {
            return 0;
        }

        return (remainingMs + 999) / 1000;
    }
}
