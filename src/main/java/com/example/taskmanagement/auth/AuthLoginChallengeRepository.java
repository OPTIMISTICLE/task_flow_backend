package com.example.taskmanagement.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthLoginChallengeRepository extends JpaRepository<AuthLoginChallenge, UUID> {
    Optional<AuthLoginChallenge> findByTokenHash(String tokenHash);
}
