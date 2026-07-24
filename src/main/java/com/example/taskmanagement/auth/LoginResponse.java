package com.example.taskmanagement.auth;

import java.time.Instant;
import java.util.UUID;

import com.example.taskmanagement.user.UserAccountStatus;
import com.example.taskmanagement.user.UserRole;

public record LoginResponse(
        String state,
        AuthUserResponse user,
        String challengeToken,
        Instant challengeExpiresAt,
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
    public static LoginResponse authenticated(AuthUserResponse user) {
        return new LoginResponse(user.mustChangePassword() ? "PASSWORD_CHANGE_REQUIRED" : "AUTHENTICATED",
                user, null, null, user.id(), user.email(), user.firstName(), user.lastName(), user.displayName(),
                user.role(), user.status(), user.mustChangePassword(), user.mfaEnabled(), user.sessionId());
    }

    public static LoginResponse challenge(LoginChallengeService.IssuedChallenge challenge) {
        return new LoginResponse("MFA_REQUIRED", null, challenge.token(), challenge.expiresAt(),
                null, null, null, null, null, null, null, false, true, null);
    }
}
