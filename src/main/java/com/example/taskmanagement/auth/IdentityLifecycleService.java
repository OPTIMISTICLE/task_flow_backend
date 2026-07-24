package com.example.taskmanagement.auth;

import com.example.taskmanagement.audit.AuditService;
import com.example.taskmanagement.common.ConflictException;
import com.example.taskmanagement.common.InvalidRequestException;
import com.example.taskmanagement.config.IdentityProperties;
import com.example.taskmanagement.email.EmailService;
import com.example.taskmanagement.user.User;
import com.example.taskmanagement.user.UserAccountStatus;
import com.example.taskmanagement.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;

@Service
public class IdentityLifecycleService {
    private final UserRepository users;
    private final SecureTokenService tokens;
    private final EmailService email;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SessionService sessions;
    private final IdentityProperties properties;
    private final AuditService audit;
    private final Clock clock;

    public IdentityLifecycleService(UserRepository users, SecureTokenService tokens, EmailService email,
                                    PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                                    SessionService sessions, IdentityProperties properties,
                                    AuditService audit, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.email = email;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.sessions = sessions;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void invite(User user) {
        if (user.getAccountStatus() != UserAccountStatus.PENDING) {
            throw new ConflictException("Only pending accounts can receive an invitation.");
        }
        String raw = tokens.issue(user, AuthTokenPurpose.INVITATION, null, properties.invitationTtl());
        email.invitation(user, raw);
    }

    @Transactional
    public void acceptInvitation(PasswordTokenRequest request) {
        passwordPolicy.validate(request.password());
        AuthActionToken token = tokens.consume(request.token(), AuthTokenPurpose.INVITATION);
        User user = token.getUser();
        if (user.getAccountStatus() != UserAccountStatus.PENDING) {
            throw new InvalidRequestException("This invitation can no longer be accepted.");
        }
        user.activate(passwordEncoder.encode(request.password()), clock.instant());
        users.saveAndFlush(user);
        audit.success(AuthenticatedUser.from(user), "AUTH_INVITATION_ACCEPTED", "USER", user.getId(),
                "Invitation accepted");
        email.securityNotice(user, "Your TaskFlow account is active",
                "Your TaskFlow invitation was accepted and a password was created.");
    }

    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        String normalized = request.email().strip().toLowerCase(Locale.ROOT);
        users.findByEmailIgnoreCase(normalized).filter(User::isActive).ifPresent(user -> {
            String raw = tokens.issue(user, AuthTokenPurpose.PASSWORD_RESET, null, properties.passwordResetTtl());
            email.passwordReset(user, raw);
            audit.success(normalized, "AUTH_PASSWORD_RESET_REQUESTED", "USER", user.getId(),
                    "Password reset email queued");
        });
    }

    @Transactional
    public void completePasswordReset(PasswordTokenRequest request) {
        passwordPolicy.validate(request.password());
        AuthActionToken token = tokens.consume(request.token(), AuthTokenPurpose.PASSWORD_RESET);
        User user = token.getUser();
        if (!user.isActive()) {
            throw new InvalidRequestException("This account is unavailable.");
        }
        if (user.hasPassword() && passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidRequestException("The new password must be different from the current password.");
        }
        user.replacePassword(passwordEncoder.encode(request.password()), false, clock.instant());
        users.saveAndFlush(user);
        sessions.revokeAll(user.getId(), "Password reset");
        audit.success(AuthenticatedUser.from(user), "AUTH_PASSWORD_RESET_COMPLETED", "USER", user.getId(),
                "Password reset completed");
        email.securityNotice(user, "Your TaskFlow password was reset",
                "Your TaskFlow password was changed using a recovery link. All sessions were signed out.");
    }

    @Transactional
    public void requestEmailChange(User user, String pendingEmail) {
        String raw = tokens.issue(user, AuthTokenPurpose.EMAIL_CHANGE, pendingEmail, properties.invitationTtl());
        email.emailChange(user, pendingEmail, raw);
    }

    @Transactional
    public void confirmEmail(TokenRequest request) {
        AuthActionToken token = tokens.consume(request.token(), AuthTokenPurpose.EMAIL_CHANGE);
        User user = token.getUser();
        String pending = token.getPendingEmail();
        if (pending == null || !pending.equalsIgnoreCase(user.getPendingEmail())) {
            throw new InvalidRequestException("This email confirmation is no longer valid.");
        }
        if (users.existsByEmailIgnoreCaseAndIdNot(pending, user.getId())) {
            throw new ConflictException("An account already uses this email address.");
        }
        String oldEmail = user.getEmail();
        user.confirmPendingEmail(clock.instant());
        users.saveAndFlush(user);
        sessions.revokeAll(user.getId(), "Email address changed");
        audit.success(AuthenticatedUser.from(user), "USER_EMAIL_CHANGED", "USER", user.getId(),
                "Email address confirmed (previous address retained only in audit actor metadata: "
                        + maskEmail(oldEmail) + ")");
        email.securityNotice(user, "Your TaskFlow email changed",
                "Your TaskFlow sign-in email was changed. All sessions were signed out.");
    }

    private String maskEmail(String value) {
        int at = value.indexOf('@');
        return at <= 1 ? "***" : value.charAt(0) + "***" + value.substring(at);
    }
}
