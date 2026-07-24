package com.example.taskmanagement.auth;

import com.example.taskmanagement.audit.AuditService;
import com.example.taskmanagement.common.InvalidRequestException;
import com.example.taskmanagement.user.User;
import com.example.taskmanagement.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;

    public PasswordService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           AuditService auditService, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public AuthenticatedUser change(ChangePasswordRequest request, AuthenticatedUser currentUser) {
        User user = userRepository.findById(currentUser.id())
                .filter(User::isActive)
                .orElseThrow(() -> new InvalidRequestException("The account is unavailable."));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            auditService.denied(currentUser, "USER_PASSWORD_CHANGE_DENIED", "Current password rejected");
            throw new InvalidRequestException("The current password is incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            auditService.denied(currentUser, "USER_PASSWORD_CHANGE_DENIED", "New password matched current password");
            throw new InvalidRequestException("The new password must be different from the current password.");
        }
        user.replacePassword(passwordEncoder.encode(request.newPassword()), false, clock.instant());
        userRepository.saveAndFlush(user);
        AuthenticatedUser updated = AuthenticatedUser.from(user);
        auditService.success(updated, "USER_PASSWORD_CHANGED", "USER", user.getId(),
                "User changed their password");
        return updated;
    }
}
