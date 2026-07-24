package com.example.taskmanagement.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 120) String jobTitle,
        @Size(max = 120) String department,
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "must use E.164 format, for example +14155552671")
        String phoneNumber,
        @NotNull UserRole role
) {
}
