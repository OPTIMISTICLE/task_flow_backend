package com.example.taskmanagement.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MfaSetupRequest(@NotBlank @Size(max = 200) String currentPassword) {
}
