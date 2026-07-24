package com.example.taskmanagement.attachment;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(
        UUID id,
        String originalName,
        String mimeType,
        long sizeBytes,
        Instant uploadedAt,
        String uploadedBy,
        String downloadUrl
) {
    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getOriginalName(),
                attachment.getMimeType(),
                attachment.getSizeBytes(),
                attachment.getUploadedAt(),
                attachment.getUploadedBy().getDisplayName(),
                "/api/tasks/" + attachment.getTask().getId() + "/attachments/" + attachment.getId()
        );
    }
}
