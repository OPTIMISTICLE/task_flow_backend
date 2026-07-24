package com.example.taskmanagement.auth;

import com.example.taskmanagement.common.InvalidRequestException;
import com.example.taskmanagement.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class SecureTokenService {
    private final AuthActionTokenRepository tokens;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public SecureTokenService(AuthActionTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional
    public String issue(User user, AuthTokenPurpose purpose, String pendingEmail, Duration ttl) {
        Instant now = clock.instant();
        tokens.findByUserIdAndPurposeAndUsedAtIsNull(user.getId(), purpose)
                .forEach(token -> token.use(now));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new AuthActionToken(user, purpose, hash(raw), pendingEmail, now, now.plus(ttl)));
        return raw;
    }

    @Transactional
    public AuthActionToken consume(String raw, AuthTokenPurpose purpose) {
        Instant now = clock.instant();
        AuthActionToken token = tokens.findByTokenHashAndPurpose(hash(raw), purpose)
                .filter(value -> value.isUsable(now))
                .orElseThrow(() -> new InvalidRequestException("This link is invalid or has expired."));
        token.use(now);
        return token;
    }

    public String opaqueToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRequestException("The security token is required.");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
