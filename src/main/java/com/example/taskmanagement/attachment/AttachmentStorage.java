package com.example.taskmanagement.attachment;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface AttachmentStorage {

    StoredFile store(MultipartFile file);

    Resource load(String storagePath);

    void deleteQuietly(String storagePath);

    record StoredFile(String storedName, String storagePath) {
    }
}
