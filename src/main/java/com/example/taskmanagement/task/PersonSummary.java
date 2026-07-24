package com.example.taskmanagement.task;

import com.example.taskmanagement.user.User;

import java.util.UUID;

public record PersonSummary(UUID id, String displayName, String email) {
    public static PersonSummary from(User user) {
        return new PersonSummary(user.getId(), user.getDisplayName(), user.getEmail());
    }
}
