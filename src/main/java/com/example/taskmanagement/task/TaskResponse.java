package com.example.taskmanagement.task;

import com.example.taskmanagement.attachment.AttachmentResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        String title,
        String description,
        Instant dueDate,
        TaskProgressStatus progressStatus,
        TaskStatus effectiveStatus,
        PersonSummary creator,
        PersonSummary assignee,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        long version,
        List<AttachmentResponse> attachments
) {
}
