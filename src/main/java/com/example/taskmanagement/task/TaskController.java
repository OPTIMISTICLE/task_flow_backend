package com.example.taskmanagement.task;

import com.example.taskmanagement.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    public List<TaskResponse> list(@RequestParam(required = false) TaskStatus status,
                                   @AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user, status);
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.get(id, user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request,
                               @AuthenticationPrincipal AuthenticatedUser user) {
        return service.create(request, user);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('WORKER')")
    public TaskResponse updateStatus(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateTaskStatusRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser user) {
        return service.updateStatus(id, request, user);
    }
}
