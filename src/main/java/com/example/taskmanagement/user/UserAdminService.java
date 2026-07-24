package com.example.taskmanagement.user;

import com.example.taskmanagement.attachment.AttachmentRepository;
import com.example.taskmanagement.audit.AuditEventRepository;
import com.example.taskmanagement.audit.AuditService;
import com.example.taskmanagement.auth.AuthenticatedUser;
import com.example.taskmanagement.auth.IdentityLifecycleService;
import com.example.taskmanagement.auth.MfaService;
import com.example.taskmanagement.auth.PasswordResetRequest;
import com.example.taskmanagement.auth.SessionService;
import com.example.taskmanagement.common.ConflictException;
import com.example.taskmanagement.common.ResourceNotFoundException;
import com.example.taskmanagement.task.TaskProgressStatus;
import com.example.taskmanagement.task.TaskRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final AttachmentRepository attachmentRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditService auditService;
    private final IdentityLifecycleService identity;
    private final SessionService sessions;
    private final MfaService mfa;
    private final Clock clock;

    public UserAdminService(UserRepository userRepository, TaskRepository taskRepository,
                            AttachmentRepository attachmentRepository, AuditEventRepository auditEventRepository,
                            AuditService auditService, IdentityLifecycleService identity,
                            SessionService sessions, MfaService mfa, Clock clock) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.attachmentRepository = attachmentRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditService = auditService;
        this.identity = identity;
        this.sessions = sessions;
        this.mfa = mfa;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminUserPageResponse list(String query, UserRole role, UserAccountStatus status, int page, int size,
                                      String sort, String direction) {
        Specification<User> specification = directorySpecification(query, role, status);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), directorySort(sort, direction));
        return AdminUserPageResponse.from(userRepository.findAll(specification, pageable));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(UUID id) {
        return AdminUserResponse.from(requireUser(id));
    }

    @Transactional
    public AdminUserResponse create(CreateUserRequest request, AuthenticatedUser actor) {
        String email = normalizeEmail(request.email());
        ensureEmailAvailable(email, null);
        User user = new User(normalizeRequired(request.firstName()), normalizeRequired(request.lastName()), email,
                null, request.role(), UserAccountStatus.PENDING, normalizeOptional(request.jobTitle()),
                normalizeOptional(request.department()), normalizeOptional(request.phoneNumber()), false,
                clock.instant());
        userRepository.saveAndFlush(user);
        identity.invite(user);
        auditService.success(actor, "USER_CREATED", "USER", user.getId(),
                "Created " + request.role().name() + " account");
        return AdminUserResponse.from(user);
    }

    @Transactional
    public AdminUserResponse update(UUID id, UpdateUserRequest request, AuthenticatedUser actor) {
        User user = requireUser(id);
        requireVersion(user, request.version());
        String email = normalizeEmail(request.email());
        ensureEmailAvailable(email, id);
        if (user.getRole() != request.role()) {
            ensureRoleCanChange(user, request.role(), actor);
            UserRole oldRole = user.getRole();
            user.changeRole(request.role(), clock.instant());
            sessions.revokeAll(user.getId(), "Role changed");
            auditService.success(actor, "USER_ROLE_CHANGED", "USER", user.getId(),
                    "Role changed from " + oldRole.name() + " to " + request.role().name());
        }
        user.updateProfile(normalizeRequired(request.firstName()), normalizeRequired(request.lastName()),
                normalizeOptional(request.jobTitle()), normalizeOptional(request.department()),
                normalizeOptional(request.phoneNumber()), clock.instant());
        if (!email.equalsIgnoreCase(user.getEmail())) {
            if (user.getAccountStatus() == UserAccountStatus.PENDING) {
                user.replaceInvitationEmail(email, clock.instant());
                identity.invite(user);
                auditService.success(actor, "USER_INVITATION_EMAIL_CHANGED", "USER", user.getId(),
                        "Invitation replaced and queued for the new address");
            } else {
                user.requestEmailChange(email, clock.instant());
                identity.requestEmailChange(user, email);
                auditService.success(actor, "USER_EMAIL_CHANGE_REQUESTED", "USER", user.getId(),
                        "Email confirmation queued");
            }
        } else if (user.getPendingEmail() != null) {
            user.clearPendingEmail(clock.instant());
        }
        userRepository.saveAndFlush(user);
        auditService.success(actor, "USER_UPDATED", "USER", user.getId(), "Profile updated");
        return AdminUserResponse.from(user);
    }

    @Transactional
    public AdminUserResponse deactivate(UUID id, UserVersionRequest request, AuthenticatedUser actor) {
        User user = requireUser(id);
        requireVersion(user, request.version());
        if (!user.isActive()) {
            return AdminUserResponse.from(user);
        }
        if (user.getId().equals(actor.id())) {
            deny(actor, "USER_DEACTIVATE_DENIED", user,
                    "You cannot deactivate your own administrator account.");
        }
        ensureNotLastAdministrator(user, actor, "USER_DEACTIVATE_DENIED");
        ensureNoOpenWork(user, actor);
        user.changeActive(false, clock.instant());
        userRepository.saveAndFlush(user);
        sessions.revokeAll(user.getId(), "Account deactivated");
        auditService.success(actor, "USER_DEACTIVATED", "USER", user.getId(), "Account deactivated");
        return AdminUserResponse.from(user);
    }

    @Transactional
    public AdminUserResponse activate(UUID id, UserVersionRequest request, AuthenticatedUser actor) {
        User user = requireUser(id);
        requireVersion(user, request.version());
        if (user.isActive()) {
            return AdminUserResponse.from(user);
        }
        if (user.hasPassword()) {
            user.changeActive(true, clock.instant());
        } else {
            user.markPending(clock.instant());
        }
        userRepository.saveAndFlush(user);
        if (user.getAccountStatus() == UserAccountStatus.PENDING) {
            identity.invite(user);
        }
        auditService.success(actor, "USER_REACTIVATED", "USER", user.getId(), "Account reactivated");
        return AdminUserResponse.from(user);
    }

    @Transactional
    public AdminUserResponse resetPassword(UUID id, UserVersionRequest request, AuthenticatedUser actor) {
        User user = requireUser(id);
        requireVersion(user, request.version());
        if (user.getId().equals(actor.id())) {
            deny(actor, "USER_PASSWORD_RESET_DENIED", user,
                    "Use Change password to update your own credentials.");
        }
        if (!user.isActive()) {
            deny(actor, "USER_PASSWORD_RESET_DENIED", user, "Only active accounts can reset a password.");
        }
        identity.requestPasswordReset(new PasswordResetRequest(user.getEmail()));
        auditService.success(actor, "USER_PASSWORD_RESET", "USER", user.getId(),
                "Password reset email queued");
        return AdminUserResponse.from(user);
    }

    @Transactional
    public AdminUserResponse resendInvitation(UUID id, UserVersionRequest request, AuthenticatedUser actor) {
        User user = requireUser(id);
        requireVersion(user, request.version());
        identity.invite(user);
        auditService.success(actor, "USER_INVITATION_RESENT", "USER", user.getId(), "Invitation email queued");
        return AdminUserResponse.from(user);
    }

    @Transactional
    public AdminUserResponse resetMfa(UUID id, UserVersionRequest request, AuthenticatedUser actor) {
        User user = requireUser(id);
        requireVersion(user, request.version());
        if (user.getId().equals(actor.id())) {
            deny(actor, "USER_MFA_RESET_DENIED", user, "Use security settings to manage your own MFA.");
        }
        mfa.adminReset(user);
        auditService.success(actor, "USER_MFA_RESET", "USER", user.getId(), "MFA reset by administrator");
        return AdminUserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserAuditPageResponse audit(UUID id, int page, int size) {
        requireUser(id);
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        return UserAuditPageResponse.from(
                auditEventRepository.findUserTimeline(id, id.toString(), pageable));
    }

    private Specification<User> directorySpecification(String query, UserRole role, UserAccountStatus status) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.strip().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("firstName")), pattern),
                        builder.like(builder.lower(root.get("lastName")), pattern),
                        builder.like(builder.lower(root.get("email")), pattern),
                        builder.like(builder.lower(root.get("jobTitle")), pattern),
                        builder.like(builder.lower(root.get("department")), pattern)
                ));
            }
            if (role != null) {
                predicates.add(builder.equal(root.get("role"), role));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("accountStatus"), status));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Sort directorySort(String sort, String direction) {
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        String property = switch (sort == null ? "createdAt" : sort) {
            case "name" -> "lastName";
            case "email" -> "email";
            case "updatedAt" -> "updatedAt";
            default -> "createdAt";
        };
        Sort result = Sort.by(sortDirection, property);
        if ("lastName".equals(property)) {
            result = result.and(Sort.by(sortDirection, "firstName"));
        }
        return result.and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private void ensureRoleCanChange(User user, UserRole requestedRole, AuthenticatedUser actor) {
        if (user.getId().equals(actor.id())) {
            deny(actor, "USER_ROLE_CHANGE_DENIED", user,
                    "You cannot change your own administrator role.");
        }
        ensureNotLastAdministrator(user, actor, "USER_ROLE_CHANGE_DENIED");
        if (taskRepository.existsByCreatorId(user.getId()) || taskRepository.existsByAssigneeId(user.getId())
                || attachmentRepository.existsByUploadedById(user.getId())) {
            deny(actor, "USER_ROLE_CHANGE_DENIED", user,
                    "The role cannot change because the account has task or attachment history.");
        }
    }

    private void ensureNoOpenWork(User user, AuthenticatedUser actor) {
        boolean hasOpenWork = user.getRole() == UserRole.MANAGER
                ? taskRepository.existsByCreatorIdAndProgressStatusNot(user.getId(), TaskProgressStatus.COMPLETED)
                : user.getRole() == UserRole.WORKER
                && taskRepository.existsByAssigneeIdAndProgressStatusNot(user.getId(), TaskProgressStatus.COMPLETED);
        if (hasOpenWork) {
            deny(actor, "USER_DEACTIVATE_DENIED", user,
                    "Resolve the account's unfinished tasks before deactivation.");
        }
    }

    private void ensureNotLastAdministrator(User user, AuthenticatedUser actor, String action) {
        if (user.getRole() == UserRole.ADMIN && user.isActive()
                && userRepository.countByRoleAndAccountStatus(UserRole.ADMIN, UserAccountStatus.ACTIVE) <= 1) {
            deny(actor, action, user,
                    "The last active administrator cannot be changed or deactivated.");
        }
    }

    private void deny(AuthenticatedUser actor, String action, User user, String message) {
        auditService.denied(actor, action, "USER", user.getId(), message);
        throw new ConflictException(message);
    }

    private void ensureEmailAvailable(String email, UUID currentId) {
        boolean used = currentId == null
                ? userRepository.existsByEmailIgnoreCase(email)
                : userRepository.existsByEmailIgnoreCaseAndIdNot(email, currentId);
        boolean pendingUsed = currentId == null
                ? userRepository.existsByPendingEmailIgnoreCase(email)
                : userRepository.existsByPendingEmailIgnoreCaseAndIdNot(email, currentId);
        if (used || pendingUsed) {
            throw new ConflictException("An account already uses this email address.");
        }
    }

    private void requireVersion(User user, long version) {
        if (user.getVersion() != version) {
            throw new ConflictException("The account changed since it was loaded. Refresh and try again.");
        }
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    private String normalizeEmail(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeRequired(String value) {
        return value.strip();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
