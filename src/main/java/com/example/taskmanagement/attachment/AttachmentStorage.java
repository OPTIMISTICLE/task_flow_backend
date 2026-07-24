package com.example.taskmanagement.attachment;

import com.example.taskmanagement.common.StorageException;
import com.example.taskmanagement.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class AttachmentStorage {

    private final Path root;

    public AttachmentStorage(StorageProperties properties) {
        this.root = Path.of(properties.directory()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new StorageException("Could not initialize attachment storage.", exception);
        }
    }

    public StoredFile store(MultipartFile file) {
        String storedName = UUID.randomUUID().toString();
        Path destination = root.resolve(storedName).normalize();
        if (!destination.startsWith(root)) {
            throw new StorageException("Invalid attachment path.");
        }
        try (var input = file.getInputStream()) {
            Files.copy(input, destination);
            return new StoredFile(storedName, destination.toString());
        } catch (IOException exception) {
            throw new StorageException("Could not store attachment.", exception);
        }
    }

    public Resource load(String storagePath) {
        try {
            Path path = Path.of(storagePath).toAbsolutePath().normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                throw new StorageException("Attachment content is unavailable.");
            }
            return new UrlResource(path.toUri());
        } catch (IOException exception) {
            throw new StorageException("Could not load attachment.", exception);
        }
    }

    public void deleteQuietly(String storagePath) {
        try {
            Path path = Path.of(storagePath).toAbsolutePath().normalize();
            if (path.startsWith(root)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Best-effort cleanup after a failed database write.
        }
    }

    public record StoredFile(String storedName, String storagePath) {
    }
}
