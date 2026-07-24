package com.example.taskmanagement.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, UUID> {
    List<MfaRecoveryCode> findByUserIdAndUsedAtIsNull(UUID userId);
    long countByUserIdAndUsedAtIsNull(UUID userId);
    void deleteByUserId(UUID userId);
}
