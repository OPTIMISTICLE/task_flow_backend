package com.example.taskmanagement.user;

public record TemporaryPasswordResponse(AdminUserResponse user, String temporaryPassword) {
}
