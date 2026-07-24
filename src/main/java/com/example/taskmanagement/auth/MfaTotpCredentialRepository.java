package com.example.taskmanagement.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MfaTotpCredentialRepository extends JpaRepository<MfaTotpCredential, UUID> {
    boolean existsByUserIdAndEnabledAtIsNotNull(UUID userId);
}
