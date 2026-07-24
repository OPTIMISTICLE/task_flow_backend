package com.example.taskmanagement.user;

import com.example.taskmanagement.audit.AuditEvent;
import org.springframework.data.domain.Page;

import java.util.List;

public record UserAuditPageResponse(
        List<UserAuditResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static UserAuditPageResponse from(Page<AuditEvent> events) {
        return new UserAuditPageResponse(events.getContent().stream().map(UserAuditResponse::from).toList(),
                events.getNumber(), events.getSize(), events.getTotalElements(), events.getTotalPages());
    }
}
