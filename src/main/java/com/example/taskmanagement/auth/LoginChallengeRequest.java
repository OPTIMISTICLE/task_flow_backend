package com.example.taskmanagement.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginChallengeRequest(
        @NotBlank String challengeToken,
        @NotBlank @Size(max = 64) String code
) {
}
