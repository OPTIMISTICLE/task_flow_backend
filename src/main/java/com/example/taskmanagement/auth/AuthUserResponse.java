package com.example.taskmanagement.auth;

import com.example.taskmanagement.user.UserRole;

import java.util.UUID;

public record AuthUserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String displayName,
        UserRole role
) {
    public static AuthUserResponse from(AuthenticatedUser user) {
        return new AuthUserResponse(user.id(), user.email(), user.firstName(), user.lastName(),
                user.displayName(), user.role());
    }
}
