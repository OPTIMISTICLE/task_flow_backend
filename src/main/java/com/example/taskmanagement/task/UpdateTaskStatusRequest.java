package com.example.taskmanagement.task;

import jakarta.validation.constraints.NotNull;

public record UpdateTaskStatusRequest(@NotNull TaskProgressStatus status) {
}
