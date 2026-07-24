package com.example.taskmanagement.auth;

import com.example.taskmanagement.audit.AuditService;
import com.example.taskmanagement.common.InvalidRequestException;
import com.example.taskmanagement.email.EmailService;
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
    private final PasswordPolicy passwordPolicy;
    private final EmailService emailService;
    private final Clock clock;

    public PasswordService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           AuditService auditService, PasswordPolicy passwordPolicy,
                           EmailService emailService, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.passwordPolicy = passwordPolicy;
        this.emailService = emailService;
        this.clock = clock;
    }

    @Transactional
    public AuthenticatedUser change(ChangePasswordRequest request, AuthenticatedUser currentUser) {
        passwordPolicy.validate(request.newPassword());
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
        emailService.securityNotice(user, "Your TaskFlow password changed",
                "Your TaskFlow password was changed. Other sessions were signed out.");
        return updated;
    }
}
