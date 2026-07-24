package com.example.taskmanagement.user;

import com.example.taskmanagement.audit.AuditEvent;

import java.time.Instant;
import java.util.UUID;

public record UserAuditResponse(
        UUID id,
        String actorEmail,
        String action,
        String outcome,
        String details,
        Instant occurredAt
) {
    public static UserAuditResponse from(AuditEvent event) {
        return new UserAuditResponse(event.getId(), event.getActorEmail(), event.getAction(), event.getOutcome(),
                event.getDetails(), event.getOccurredAt());
    }
}
