package com.example.taskmanagement.auth;

public record MfaStatusResponse(boolean enabled, long recoveryCodesRemaining) {
}
