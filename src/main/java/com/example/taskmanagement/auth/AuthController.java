package com.example.taskmanagement.auth;

import com.example.taskmanagement.audit.AuditService;
import com.example.taskmanagement.common.InvalidRequestException;
import com.example.taskmanagement.email.EmailService;
import com.example.taskmanagement.user.User;
import com.example.taskmanagement.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final SessionService sessions;
    private final UserRepository users;
    private final MfaService mfa;
    private final LoginChallengeService challenges;
    private final IdentityLifecycleService identity;
    private final AuditService audit;
    private final EmailService email;
    private final PasswordService passwords;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
                          SessionService sessions, UserRepository users, MfaService mfa,
                          LoginChallengeService challenges, IdentityLifecycleService identity,
                          AuditService audit, EmailService email, PasswordService passwords) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.sessions = sessions;
        this.users = users;
        this.mfa = mfa;
        this.challenges = challenges;
        this.identity = identity;
        this.audit = audit;
        this.email = email;
        this.passwords = passwords;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return new CsrfResponse(csrfToken.getHeaderName());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
                               HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
            AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
            User user = requireActiveUser(principal.id());
            if (mfa.enabled(user.getId())) {
                return LoginResponse.challenge(challenges.issue(user));
            }
            return completeLogin(user, httpRequest, response);
        } catch (AuthenticationException exception) {
            audit.failure(request.email(), "AUTH_LOGIN", "USER", null, "Authentication rejected");
            throw exception;
        }
    }

    @PostMapping("/mfa/challenge")
    public LoginResponse completeMfa(@Valid @RequestBody LoginChallengeRequest request,
                                     HttpServletRequest httpRequest, HttpServletResponse response) {
        User user = challenges.verify(request.challengeToken(), request.code());
        return completeLogin(user, httpRequest, response);
    }

    @GetMapping("/me")
    public AuthUserResponse me(Authentication authentication) {
        AuthenticatedUser user = principal(authentication);
        return AuthUserResponse.from(user, mfa.enabled(user.id()), currentSession(authentication));
    }

    @PostMapping("/change-password")
    public AuthUserResponse changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                           Authentication authentication, HttpServletRequest httpRequest,
                                           HttpServletResponse response) {
        AuthenticatedUser current = principal(authentication);
        AuthenticatedUser updated = passwords.change(request, current);
        sessions.revokeAll(updated.id(), "Password changed");
        User user = requireActiveUser(updated.id());
        AuthSession session = sessions.create(user, httpRequest.getHeader(HttpHeaders.USER_AGENT));
        setAccessCookie(response, updated, session.getId());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return AuthUserResponse.from(updated, mfa.enabled(updated.id()), session.getId());
    }

    @PostMapping("/logout")
    public void logout(Authentication authentication, HttpServletResponse response) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            sessions.revoke(user.id(), currentSession(authentication), "User logout");
            audit.success(user, "AUTH_LOGOUT", "USER", user.id(), "Session revoked");
        }
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie("", Duration.ZERO).toString());
    }

    @PostMapping("/invitations/accept")
    public MessageResponse acceptInvitation(@Valid @RequestBody PasswordTokenRequest request) {
        identity.acceptInvitation(request);
        return new MessageResponse("Your account is active. You can now sign in.");
    }

    @PostMapping("/password-recovery/request")
    public MessageResponse requestRecovery(@Valid @RequestBody PasswordResetRequest request) {
        identity.requestPasswordReset(request);
        return new MessageResponse("If the account exists, a password reset email has been sent.");
    }

    @PostMapping("/password-recovery/complete")
    public MessageResponse completeRecovery(@Valid @RequestBody PasswordTokenRequest request) {
        identity.completePasswordReset(request);
        return new MessageResponse("Your password was reset. Sign in with the new password.");
    }

    @PostMapping("/email/confirm")
    public MessageResponse confirmEmail(@Valid @RequestBody TokenRequest request) {
        identity.confirmEmail(request);
        return new MessageResponse("Your email address was confirmed. Sign in again.");
    }

    private LoginResponse completeLogin(User user, HttpServletRequest request, HttpServletResponse response) {
        AuthenticatedUser principal = AuthenticatedUser.from(user);
        AuthSession session = sessions.create(user, request.getHeader(HttpHeaders.USER_AGENT));
        setAccessCookie(response, principal, session.getId());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        audit.success(principal, "AUTH_LOGIN", "USER", user.getId(), "Authentication succeeded");
        email.securityNotice(user, "New TaskFlow sign-in",
                "A successful sign-in to your TaskFlow account was recorded.");
        return LoginResponse.authenticated(AuthUserResponse.from(principal, mfa.enabled(user.getId()),
                session.getId()));
    }

    private void setAccessCookie(HttpServletResponse response, AuthenticatedUser user, UUID sessionId) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                accessCookie(jwtService.issue(user, sessionId), jwtService.properties().ttl()).toString());
    }

    private ResponseCookie accessCookie(String value, Duration maxAge) {
        return ResponseCookie.from(jwtService.properties().cookieName(), value)
                .httpOnly(true).secure(jwtService.properties().cookieSecure())
                .sameSite(jwtService.properties().cookieSameSite()).path("/api").maxAge(maxAge).build();
    }

    private User requireActiveUser(UUID id) {
        return users.findById(id).filter(User::isActive)
                .orElseThrow(() -> new InvalidRequestException("The account is unavailable."));
    }

    static AuthenticatedUser principal(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    static UUID currentSession(Authentication authentication) {
        if (authentication.getDetails() instanceof JwtAuthenticationFilter.AuthSessionDetails details) {
            return details.sessionId();
        }
        throw new InvalidRequestException("The current session is unavailable.");
    }
}
