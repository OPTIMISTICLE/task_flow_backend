package com.example.taskmanagement.user;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        long version,
        String firstName,
        String lastName,
        String displayName,
        String email,
        String jobTitle,
        String department,
        String phoneNumber,
        UserRole role,
        boolean active,
        boolean mustChangePassword,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.getId(), user.getVersion(), user.getFirstName(), user.getLastName(),
                user.getDisplayName(), user.getEmail(), user.getJobTitle(), user.getDepartment(),
                user.getPhoneNumber(), user.getRole(), user.isActive(), user.isMustChangePassword(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
