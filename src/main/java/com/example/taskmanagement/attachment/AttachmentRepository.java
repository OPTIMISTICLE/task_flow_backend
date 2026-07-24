package com.example.taskmanagement.attachment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    List<Attachment> findByTaskIdOrderByUploadedAtAsc(UUID taskId);

    Optional<Attachment> findByIdAndTaskId(UUID id, UUID taskId);
}
