package com.example.taskmanagement.attachment;

import com.example.taskmanagement.audit.AuditService;
import com.example.taskmanagement.auth.AuthenticatedUser;
import com.example.taskmanagement.common.InvalidRequestException;
import com.example.taskmanagement.common.ResourceNotFoundException;
import com.example.taskmanagement.task.TaskEntity;
import com.example.taskmanagement.task.TaskService;
import com.example.taskmanagement.user.User;
import com.example.taskmanagement.user.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.UUID;

@Service
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final TaskService taskService;
    private final AttachmentStorage storage;
    private final AuditService auditService;

    public AttachmentService(AttachmentRepository attachmentRepository, UserRepository userRepository,
                             TaskService taskService, AttachmentStorage storage, AuditService auditService) {
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.taskService = taskService;
        this.storage = storage;
        this.auditService = auditService;
    }

    @Transactional
    public AttachmentResponse upload(UUID taskId, MultipartFile file, AuthenticatedUser currentUser) {
        TaskEntity task = taskService.requireAccessibleEntity(taskId, currentUser);
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("An attachment file is required.");
        }
        String originalName = safeOriginalName(file.getOriginalFilename());
        String mimeType = file.getContentType() == null || file.getContentType().isBlank()
                ? "application/octet-stream" : file.getContentType();
        User uploadedBy = userRepository.getReferenceById(currentUser.id());
        AttachmentStorage.StoredFile stored = storage.store(file);
        try {
            Attachment attachment = attachmentRepository.saveAndFlush(new Attachment(
                    task, uploadedBy, originalName, stored.storedName(), stored.storagePath(), mimeType, file.getSize()));
            auditService.success(currentUser, "ATTACHMENT_UPLOADED", "TASK", taskId,
                    "Attachment " + attachment.getId());
            return AttachmentResponse.from(attachment);
        } catch (RuntimeException exception) {
            storage.deleteQuietly(stored.storagePath());
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public AttachmentDownload download(UUID taskId, UUID attachmentId, AuthenticatedUser currentUser) {
        taskService.requireAccessibleEntity(taskId, currentUser);
        Attachment attachment = attachmentRepository.findByIdAndTaskId(attachmentId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found."));
        Resource resource = storage.load(attachment.getStoragePath());
        auditService.success(currentUser, "ATTACHMENT_DOWNLOADED", "TASK", taskId,
                "Attachment " + attachmentId);
        return new AttachmentDownload(resource, attachment.getOriginalName(), attachment.getMimeType(),
                attachment.getSizeBytes());
    }

    private String safeOriginalName(String originalName) {
        String value = originalName == null ? "attachment" : Path.of(originalName).getFileName().toString();
        value = value.replaceAll("[\\p{Cntrl}]", "_").trim();
        if (value.isBlank()) {
            value = "attachment";
        }
        return value.substring(0, Math.min(value.length(), 255));
    }
}
