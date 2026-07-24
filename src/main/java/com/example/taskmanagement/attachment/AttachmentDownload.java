package com.example.taskmanagement.attachment;

import org.springframework.core.io.Resource;

public record AttachmentDownload(Resource resource, String originalName, String mimeType, long sizeBytes) {
}
