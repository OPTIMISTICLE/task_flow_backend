package com.example.taskmanagement.auth;

public record MfaSetupResponse(String secret, String otpauthUri) {
}
