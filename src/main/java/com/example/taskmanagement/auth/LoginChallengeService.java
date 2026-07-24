package com.example.taskmanagement.auth;

import com.example.taskmanagement.common.InvalidRequestException;
import com.example.taskmanagement.config.IdentityProperties;
import com.example.taskmanagement.user.User;
import com.example.taskmanagement.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class LoginChallengeService {
    private final AuthLoginChallengeRepository challenges;
    private final SecureTokenService tokens;
    private final MfaService mfa;
    private final IdentityProperties properties;
    private final UserRepository users;
    private final Clock clock;

    public LoginChallengeService(AuthLoginChallengeRepository challenges, SecureTokenService tokens,
                                 MfaService mfa, IdentityProperties properties,
                                 UserRepository users, Clock clock) {
        this.challenges = challenges;
        this.tokens = tokens;
        this.mfa = mfa;
        this.properties = properties;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public IssuedChallenge issue(User user) {
        String raw = tokens.opaqueToken();
        Instant expires = clock.instant().plus(properties.loginChallengeTtl());
        challenges.save(new AuthLoginChallenge(user, tokens.hash(raw), clock.instant(), expires));
        return new IssuedChallenge(raw, expires);
    }

    @Transactional(noRollbackFor = InvalidRequestException.class)
    public User verify(String rawChallenge, String code) {
        AuthLoginChallenge challenge = challenges.findByTokenHash(tokens.hash(rawChallenge))
                .orElseThrow(() -> new InvalidRequestException("The login challenge is invalid or has expired."));
        if (!challenge.isUsable(clock.instant())) {
            throw new InvalidRequestException("The login challenge is invalid or has expired.");
        }
        User user = users.findById(challenge.getUser().getId()).filter(User::isActive)
                .orElseThrow(() -> new InvalidRequestException("The account is unavailable."));
        if (!mfa.verify(user, code)) {
            challenge.failAttempt();
            throw new InvalidRequestException("The authenticator or recovery code is invalid.");
        }
        challenge.use(clock.instant());
        return user;
    }

    public record IssuedChallenge(String token, Instant expiresAt) {
    }
}
