package com.example.taskmanagement.auth;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenRevocationService {

    private final Map<String, Instant> revokedTokens = new ConcurrentHashMap<>();
    private final Clock clock;

    public TokenRevocationService(Clock clock) {
        this.clock = clock;
    }

    public void revoke(Jwt jwt) {
        if (jwt.getId() != null && jwt.getExpiresAt() != null) {
            revokedTokens.put(jwt.getId(), jwt.getExpiresAt());
            removeExpired();
        }
    }

    public boolean isRevoked(Jwt jwt) {
        removeExpired();
        return jwt.getId() != null && revokedTokens.containsKey(jwt.getId());
    }

    private void removeExpired() {
        Instant now = clock.instant();
        revokedTokens.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }
}
