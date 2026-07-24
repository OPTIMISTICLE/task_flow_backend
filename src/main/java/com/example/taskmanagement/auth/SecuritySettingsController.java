package com.example.taskmanagement.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class SecuritySettingsController {
    private final SessionService sessions;
    private final MfaService mfa;

    public SecuritySettingsController(SessionService sessions, MfaService mfa) {
        this.sessions = sessions;
        this.mfa = mfa;
    }

    @GetMapping("/sessions")
    public List<SessionResponse> sessions(Authentication authentication) {
        AuthenticatedUser user = AuthController.principal(authentication);
        return sessions.list(user.id(), AuthController.currentSession(authentication));
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id, Authentication authentication) {
        AuthenticatedUser user = AuthController.principal(authentication);
        sessions.revoke(user.id(), id, "Revoked by user");
    }

    @PostMapping("/sessions/revoke-others")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOthers(Authentication authentication) {
        AuthenticatedUser user = AuthController.principal(authentication);
        sessions.revokeOthers(user.id(), AuthController.currentSession(authentication), "Revoked by user");
    }

    @GetMapping("/mfa")
    public MfaStatusResponse mfaStatus(Authentication authentication) {
        return mfa.status(AuthController.principal(authentication).id());
    }

    @PostMapping("/mfa/setup")
    public MfaSetupResponse setup(@Valid @RequestBody MfaSetupRequest request,
                                  Authentication authentication) {
        return mfa.setup(AuthController.principal(authentication), request);
    }

    @PostMapping("/mfa/confirm")
    public RecoveryCodesResponse confirm(@Valid @RequestBody MfaCodeRequest request,
                                         Authentication authentication) {
        return mfa.confirm(AuthController.principal(authentication), request,
                AuthController.currentSession(authentication));
    }

    @PostMapping("/mfa/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@Valid @RequestBody MfaSensitiveRequest request,
                        Authentication authentication) {
        mfa.disable(AuthController.principal(authentication), request,
                AuthController.currentSession(authentication));
    }

    @PostMapping("/mfa/recovery-codes")
    public RecoveryCodesResponse regenerate(@Valid @RequestBody MfaSensitiveRequest request,
                                            Authentication authentication) {
        return mfa.regenerate(AuthController.principal(authentication), request);
    }
}
