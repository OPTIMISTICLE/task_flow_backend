package com.example.taskmanagement.auth;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(UUID id, String userAgent, Instant createdAt, Instant lastSeenAt,
                              Instant expiresAt, boolean current, boolean active) {
    static SessionResponse from(AuthSession session, UUID currentId, Instant now) {
        return new SessionResponse(session.getId(), session.getUserAgent(), session.getCreatedAt(),
                session.getLastSeenAt(), session.getExpiresAt(), session.getId().equals(currentId),
                session.isActive(now));
    }
}
