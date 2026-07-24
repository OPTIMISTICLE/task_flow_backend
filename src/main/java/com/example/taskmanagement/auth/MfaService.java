package com.example.taskmanagement.auth;

import com.example.taskmanagement.audit.AuditService;
import com.example.taskmanagement.common.InvalidRequestException;
import com.example.taskmanagement.email.EmailService;
import com.example.taskmanagement.user.User;
import com.example.taskmanagement.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class MfaService {
    private final UserRepository users;
    private final MfaTotpCredentialRepository credentials;
    private final MfaRecoveryCodeRepository recoveryCodes;
    private final MfaCryptoService crypto;
    private final TotpService totp;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final AuditService audit;
    private final EmailService email;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public MfaService(UserRepository users, MfaTotpCredentialRepository credentials,
                      MfaRecoveryCodeRepository recoveryCodes, MfaCryptoService crypto,
                      TotpService totp, PasswordEncoder passwordEncoder, SessionService sessions,
                      AuditService audit, EmailService email, Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.recoveryCodes = recoveryCodes;
        this.crypto = crypto;
        this.totp = totp;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.audit = audit;
        this.email = email;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean enabled(UUID userId) {
        return credentials.existsByUserIdAndEnabledAtIsNotNull(userId);
    }

    @Transactional(readOnly = true)
    public MfaStatusResponse status(UUID userId) {
        return new MfaStatusResponse(enabled(userId), recoveryCodes.countByUserIdAndUsedAtIsNull(userId));
    }

    @Transactional
    public MfaSetupResponse setup(AuthenticatedUser actor, MfaSetupRequest request) {
        User user = requireUser(actor.id());
        verifyPassword(user, request.currentPassword());
        if (enabled(user.getId())) {
            throw new InvalidRequestException("Multi-factor authentication is already enabled.");
        }
        byte[] raw = new byte[20];
        random.nextBytes(raw);
        String secret = totp.encodeBase32(raw);
        MfaCryptoService.EncryptedSecret encrypted = crypto.encrypt(secret);
        credentials.findById(user.getId()).ifPresent(credentials::delete);
        credentials.save(new MfaTotpCredential(user, encrypted.ciphertext(), encrypted.nonce()));
        String uri = UriComponentsBuilder.fromUriString("otpauth://totp/TaskFlow:" + user.getEmail())
                .queryParam("secret", secret).queryParam("issuer", "TaskFlow")
                .queryParam("algorithm", "SHA1").queryParam("digits", 6).queryParam("period", 30)
                .build().encode().toUriString();
        audit.success(actor, "MFA_SETUP_STARTED", "USER", user.getId(), "TOTP setup started");
        return new MfaSetupResponse(secret, uri);
    }

    @Transactional
    public RecoveryCodesResponse confirm(AuthenticatedUser actor, MfaCodeRequest request, UUID currentSessionId) {
        User user = requireUser(actor.id());
        MfaTotpCredential credential = credentials.findById(user.getId())
                .filter(value -> !value.isEnabled())
                .orElseThrow(() -> new InvalidRequestException("Start MFA setup before confirming it."));
        TotpService.Match match = totp.verify(crypto.decrypt(credential), request.code(), clock.instant(), null);
        if (match == null) {
            throw new InvalidRequestException("The authenticator code is invalid.");
        }
        credential.recordStep(match.step());
        credential.enable(clock.instant());
        List<String> rawCodes = replaceRecoveryCodes(user);
        sessions.revokeOthers(user.getId(), currentSessionId, "MFA enabled");
        audit.success(actor, "MFA_ENABLED", "USER", user.getId(), "TOTP MFA enabled");
        email.securityNotice(user, "Multi-factor authentication enabled",
                "Authenticator-based MFA was enabled for your TaskFlow account.");
        return new RecoveryCodesResponse(rawCodes);
    }

    @Transactional
    public void disable(AuthenticatedUser actor, MfaSensitiveRequest request, UUID currentSessionId) {
        User user = requireUser(actor.id());
        verifyPassword(user, request.currentPassword());
        if (!verify(user, request.code())) {
            throw new InvalidRequestException("The authenticator or recovery code is invalid.");
        }
        credentials.findById(user.getId()).ifPresent(credentials::delete);
        recoveryCodes.deleteByUserId(user.getId());
        sessions.revokeOthers(user.getId(), currentSessionId, "MFA disabled");
        audit.success(actor, "MFA_DISABLED", "USER", user.getId(), "MFA disabled");
        email.securityNotice(user, "Multi-factor authentication disabled",
                "MFA was disabled for your TaskFlow account.");
    }

    @Transactional
    public RecoveryCodesResponse regenerate(AuthenticatedUser actor, MfaSensitiveRequest request) {
        User user = requireUser(actor.id());
        verifyPassword(user, request.currentPassword());
        if (!verify(user, request.code())) {
            throw new InvalidRequestException("The authenticator or recovery code is invalid.");
        }
        List<String> rawCodes = replaceRecoveryCodes(user);
        audit.success(actor, "MFA_RECOVERY_CODES_REGENERATED", "USER", user.getId(),
                "Recovery codes regenerated");
        email.securityNotice(user, "TaskFlow recovery codes regenerated",
                "New MFA recovery codes were generated for your TaskFlow account.");
        return new RecoveryCodesResponse(rawCodes);
    }

    @Transactional
    public boolean verify(User user, String code) {
        MfaTotpCredential credential = credentials.findById(user.getId())
                .filter(MfaTotpCredential::isEnabled).orElse(null);
        if (credential == null) return false;
        TotpService.Match match = totp.verify(crypto.decrypt(credential), code, clock.instant(),
                credential.getLastUsedStep());
        if (match != null) {
            credential.recordStep(match.step());
            return true;
        }
        for (MfaRecoveryCode recoveryCode : recoveryCodes.findByUserIdAndUsedAtIsNull(user.getId())) {
            if (passwordEncoder.matches(normalizeRecoveryCode(code), recoveryCode.getCodeHash())) {
                recoveryCode.use(clock.instant());
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void adminReset(User user) {
        credentials.findById(user.getId()).ifPresent(credentials::delete);
        recoveryCodes.deleteByUserId(user.getId());
        user.invalidateAuthentication(clock.instant());
        users.saveAndFlush(user);
        sessions.revokeAll(user.getId(), "MFA reset by administrator");
        email.securityNotice(user, "TaskFlow MFA reset",
                "An administrator reset MFA for your TaskFlow account. All sessions were signed out.");
    }

    private List<String> replaceRecoveryCodes(User user) {
        recoveryCodes.deleteByUserId(user.getId());
        List<String> rawCodes = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            byte[] bytes = new byte[8];
            random.nextBytes(bytes);
            String raw = HexFormat.of().formatHex(bytes).toUpperCase(java.util.Locale.ROOT);
            String formatted = raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-"
                    + raw.substring(8, 12) + "-" + raw.substring(12);
            rawCodes.add(formatted);
            recoveryCodes.save(new MfaRecoveryCode(user,
                    passwordEncoder.encode(normalizeRecoveryCode(formatted)), clock.instant()));
        }
        return List.copyOf(rawCodes);
    }

    private void verifyPassword(User user, String password) {
        if (!user.hasPassword() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidRequestException("The current password is incorrect.");
        }
    }

    private User requireUser(UUID id) {
        return users.findById(id).filter(User::isActive)
                .orElseThrow(() -> new InvalidRequestException("The account is unavailable."));
    }

    private String normalizeRecoveryCode(String value) {
        return value == null ? "" : value.replace("-", "").strip().toUpperCase(java.util.Locale.ROOT);
    }
}
