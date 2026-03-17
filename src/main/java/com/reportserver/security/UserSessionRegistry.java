package com.reportserver.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserSessionRegistry {

    private final ConcurrentHashMap<String, Set<String>> sessionsByUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HttpSession> sessionStore = new ConcurrentHashMap<>();

    public void registerSession(String username, HttpSession session) {
        if (username == null || username.isBlank() || session == null) {
            return;
        }

        sessionStore.put(session.getId(), session);
        sessionsByUser.compute(username, (key, value) -> {
            Set<String> next = value == null ? new HashSet<>() : new HashSet<>(value);
            next.add(session.getId());
            return next;
        });
    }

    public void unregisterSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        sessionStore.remove(sessionId);
        for (String username : sessionsByUser.keySet()) {
            sessionsByUser.computeIfPresent(username, (key, value) -> {
                Set<String> next = new HashSet<>(value);
                next.remove(sessionId);
                return next.isEmpty() ? null : next;
            });
        }
    }

    public void invalidateSessionsForUser(String username, String exceptSessionId) {
        if (username == null || username.isBlank()) {
            return;
        }

        Set<String> sessionIds = sessionsByUser.get(username);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }

        for (String sessionId : new HashSet<>(sessionIds)) {
            if (exceptSessionId != null && exceptSessionId.equals(sessionId)) {
                continue;
            }

            HttpSession session = sessionStore.remove(sessionId);
            if (session != null) {
                try {
                    session.invalidate();
                } catch (IllegalStateException ignored) {
                    // Session already invalidated.
                }
            }

            unregisterSession(sessionId);
        }
    }
}
