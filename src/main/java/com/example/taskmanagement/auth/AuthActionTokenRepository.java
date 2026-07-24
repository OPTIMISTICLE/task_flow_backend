package com.example.taskmanagement.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthActionTokenRepository extends JpaRepository<AuthActionToken, UUID> {
    Optional<AuthActionToken> findByTokenHashAndPurpose(String tokenHash, AuthTokenPurpose purpose);
    List<AuthActionToken> findByUserIdAndPurposeAndUsedAtIsNull(UUID userId, AuthTokenPurpose purpose);
}
