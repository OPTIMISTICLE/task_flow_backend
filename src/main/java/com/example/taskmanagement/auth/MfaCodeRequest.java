package com.example.taskmanagement.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MfaCodeRequest(@NotBlank @Size(max = 64) String code) {
}
