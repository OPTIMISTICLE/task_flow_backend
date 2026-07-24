package com.example.taskmanagement.auth;

import com.example.taskmanagement.config.IdentityProperties;
import com.example.taskmanagement.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SessionService {
    private final AuthSessionRepository sessions;
    private final IdentityProperties properties;
    private final Clock clock;

    public SessionService(AuthSessionRepository sessions, IdentityProperties properties, Clock clock) {
        this.sessions = sessions;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public AuthSession create(User user, String userAgent) {
        Instant now = clock.instant();
        String safeAgent = userAgent == null || userAgent.isBlank() ? "Unknown device" : userAgent.strip();
        safeAgent = safeAgent.substring(0, Math.min(safeAgent.length(), 512));
        return sessions.save(new AuthSession(user, safeAgent, now, now.plus(properties.sessionTtl())));
    }

    @Transactional
    public boolean validateAndTouch(UUID sessionId, UUID userId) {
        Instant now = clock.instant();
        return sessions.findById(sessionId)
                .filter(session -> session.getUser().getId().equals(userId))
                .filter(session -> session.isActive(now))
                .map(session -> {
                    session.touch(now);
                    return true;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list(UUID userId, UUID currentId) {
        Instant now = clock.instant();
        return sessions.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(session -> SessionResponse.from(session, currentId, now)).toList();
    }

    @Transactional
    public boolean revoke(UUID userId, UUID sessionId, String reason) {
        return sessions.findById(sessionId)
                .filter(session -> session.getUser().getId().equals(userId))
                .map(session -> {
                    session.revoke(clock.instant(), reason);
                    return true;
                }).orElse(false);
    }

    @Transactional
    public void revokeAll(UUID userId, String reason) {
        sessions.revokeAll(userId, clock.instant(), reason);
    }

    @Transactional
    public void revokeOthers(UUID userId, UUID currentId, String reason) {
        sessions.revokeOthers(userId, currentId, clock.instant(), reason);
    }
}
