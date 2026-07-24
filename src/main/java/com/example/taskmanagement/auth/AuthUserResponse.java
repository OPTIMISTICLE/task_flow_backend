package com.example.taskmanagement.auth;

import com.example.taskmanagement.user.UserAccountStatus;
import com.example.taskmanagement.user.UserRole;

import java.util.UUID;

public record AuthUserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String displayName,
        UserRole role,
        UserAccountStatus status,
        boolean mustChangePassword,
        boolean mfaEnabled,
        UUID sessionId
) {
    public static AuthUserResponse from(AuthenticatedUser user, boolean mfaEnabled, UUID sessionId) {
        return new AuthUserResponse(user.id(), user.email(), user.firstName(), user.lastName(),
                user.displayName(), user.role(), user.active() ? UserAccountStatus.ACTIVE : UserAccountStatus.INACTIVE,
                user.mustChangePassword(), mfaEnabled, sessionId);
    }
}
