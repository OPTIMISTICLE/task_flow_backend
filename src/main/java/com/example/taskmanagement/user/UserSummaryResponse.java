package com.example.taskmanagement.user;

import java.util.UUID;

public record UserSummaryResponse(UUID id, String firstName, String lastName, String displayName, String email) {
    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getFirstName(), user.getLastName(),
                user.getDisplayName(), user.getEmail());
    }
}
