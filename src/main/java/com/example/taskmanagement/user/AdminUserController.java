package com.example.taskmanagement.user;

import com.example.taskmanagement.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserAdminService service;

    public AdminUserController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    public AdminUserPageResponse list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserAccountStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return service.list(query, role, status, page, size, sort, direction);
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<AdminUserResponse> create(@Valid @RequestBody CreateUserRequest request,
                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(service.create(request, actor));
    }

    @PatchMapping("/{id}")
    public AdminUserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.update(id, request, actor);
    }

    @PostMapping("/{id}/deactivate")
    public AdminUserResponse deactivate(@PathVariable UUID id, @Valid @RequestBody UserVersionRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.deactivate(id, request, actor);
    }

    @PostMapping("/{id}/activate")
    public AdminUserResponse activate(@PathVariable UUID id, @Valid @RequestBody UserVersionRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.activate(id, request, actor);
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<AdminUserResponse> resetPassword(
            @PathVariable UUID id, @Valid @RequestBody UserVersionRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.resetPassword(id, request, actor));
    }

    @PostMapping("/{id}/resend-invitation")
    public AdminUserResponse resendInvitation(@PathVariable UUID id,
                                              @Valid @RequestBody UserVersionRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.resendInvitation(id, request, actor);
    }

    @PostMapping("/{id}/reset-mfa")
    public AdminUserResponse resetMfa(@PathVariable UUID id,
                                      @Valid @RequestBody UserVersionRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.resetMfa(id, request, actor);
    }

    @GetMapping("/{id}/audit")
    public UserAuditPageResponse audit(@PathVariable UUID id,
                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.audit(id, page, size);
    }
}
