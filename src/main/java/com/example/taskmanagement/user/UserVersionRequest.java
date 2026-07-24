package com.example.taskmanagement.user;

import jakarta.validation.constraints.PositiveOrZero;

public record UserVersionRequest(@PositiveOrZero long version) {
}
