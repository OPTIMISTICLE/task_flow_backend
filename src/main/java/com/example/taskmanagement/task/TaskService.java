package com.example.taskmanagement.task;

import com.example.taskmanagement.attachment.AttachmentRepository;
import com.example.taskmanagement.attachment.AttachmentResponse;
import com.example.taskmanagement.audit.AuditService;
import com.example.taskmanagement.auth.AuthenticatedUser;
import com.example.taskmanagement.common.ConflictException;
import com.example.taskmanagement.common.ForbiddenOperationException;
import com.example.taskmanagement.common.InvalidRequestException;
import com.example.taskmanagement.common.ResourceNotFoundException;
import com.example.taskmanagement.user.User;
import com.example.taskmanagement.user.UserRepository;
import com.example.taskmanagement.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final AttachmentRepository attachmentRepository;
    private final AuditService auditService;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository,
                       AttachmentRepository attachmentRepository, AuditService auditService, Clock clock) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.attachmentRepository = attachmentRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request, AuthenticatedUser currentUser) {
        if (currentUser.role() != UserRole.MANAGER) {
            throw new ForbiddenOperationException("Only managers can create tasks.");
        }
        User manager = activeUser(currentUser.id());
        User assignee = userRepository.findById(request.assigneeId())
                .filter(User::isActive)
                .filter(user -> user.getRole() == UserRole.WORKER)
                .orElseThrow(() -> new InvalidRequestException("The assignee must be an active worker."));
        String title = request.title().trim();
        String description = normalizeDescription(request.description());
        TaskEntity task = taskRepository.saveAndFlush(
                new TaskEntity(title, description, request.dueDate(), manager, assignee));
        auditService.success(currentUser, "TASK_CREATED", "TASK", task.getId(),
                "Assigned to worker " + assignee.getId());
        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(AuthenticatedUser currentUser, TaskStatus status) {
        List<TaskEntity> tasks = currentUser.role() == UserRole.MANAGER
                ? taskRepository.findByCreatorIdOrderByCreatedAtDesc(currentUser.id())
                : taskRepository.findByAssigneeIdOrderByCreatedAtDesc(currentUser.id());
        return tasks.stream()
                .filter(task -> status == null || effectiveStatus(task) == status)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(UUID id, AuthenticatedUser currentUser) {
        return toResponse(requireAccessibleEntity(id, currentUser));
    }

    @Transactional
    public TaskResponse updateStatus(UUID id, UpdateTaskStatusRequest request, AuthenticatedUser currentUser) {
        if (currentUser.role() != UserRole.WORKER) {
            throw new ForbiddenOperationException("Only the assigned worker can update task status.");
        }
        TaskEntity task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found."));
        if (!task.getAssignee().getId().equals(currentUser.id())) {
            throw new ForbiddenOperationException("You cannot update a task assigned to another worker.");
        }
        TaskProgressStatus requested = request.status();
        if (requested == TaskProgressStatus.ASSIGNED) {
            throw new InvalidRequestException("A worker cannot move a task back to ASSIGNED.");
        }
        if (task.getProgressStatus() == requested) {
            return toResponse(task);
        }
        if (task.getProgressStatus() == TaskProgressStatus.COMPLETED) {
            throw new ConflictException("A completed task is final and cannot be changed.");
        }
        if (task.getProgressStatus() == TaskProgressStatus.IN_PROGRESS
                && requested != TaskProgressStatus.COMPLETED) {
            throw new ConflictException("The only valid next status is COMPLETED.");
        }
        task.changeStatus(requested, clock.instant());
        taskRepository.saveAndFlush(task);
        auditService.success(currentUser, "TASK_STATUS_CHANGED", "TASK", task.getId(),
                "Status changed to " + requested.name());
        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public TaskEntity requireAccessibleEntity(UUID id, AuthenticatedUser currentUser) {
        TaskEntity task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found."));
        boolean accessible = currentUser.role() == UserRole.MANAGER
                ? task.getCreator().getId().equals(currentUser.id())
                : task.getAssignee().getId().equals(currentUser.id());
        if (!accessible) {
            auditService.denied(currentUser, "TASK_ACCESS_DENIED", "Task " + id);
            throw new ForbiddenOperationException("You are not allowed to access this task.");
        }
        return task;
    }

    private User activeUser(UUID id) {
        return userRepository.findById(id)
                .filter(User::isActive)
                .orElseThrow(() -> new ForbiddenOperationException("The current user is inactive."));
    }

    private TaskResponse toResponse(TaskEntity task) {
        List<AttachmentResponse> attachments = attachmentRepository
                .findByTaskIdOrderByUploadedAtAsc(task.getId()).stream()
                .map(AttachmentResponse::from)
                .toList();
        return new TaskResponse(
                task.getId(), task.getTitle(), task.getDescription(), task.getDueDate(),
                task.getProgressStatus(), effectiveStatus(task),
                PersonSummary.from(task.getCreator()), PersonSummary.from(task.getAssignee()),
                task.getCreatedAt(), task.getUpdatedAt(), task.getCompletedAt(), task.getVersion(), attachments
        );
    }

    private TaskStatus effectiveStatus(TaskEntity task) {
        if (task.getProgressStatus() == TaskProgressStatus.COMPLETED) {
            return TaskStatus.COMPLETED;
        }
        Instant dueDate = task.getDueDate();
        if (dueDate != null && dueDate.isBefore(clock.instant())) {
            return TaskStatus.OVERDUE;
        }
        return TaskStatus.valueOf(task.getProgressStatus().name());
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
