package com.example.taskmanagement.auth;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(@NotBlank String token) {
}
