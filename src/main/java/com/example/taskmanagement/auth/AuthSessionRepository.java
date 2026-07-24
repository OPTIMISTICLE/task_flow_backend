package com.example.taskmanagement.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    List<AuthSession> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Query("update AuthSession s set s.revokedAt = :now, s.revokeReason = :reason "
            + "where s.user.id = :userId and s.revokedAt is null")
    int revokeAll(UUID userId, Instant now, String reason);

    @Modifying
    @Query("update AuthSession s set s.revokedAt = :now, s.revokeReason = :reason "
            + "where s.user.id = :userId and s.id <> :currentId and s.revokedAt is null")
    int revokeOthers(UUID userId, UUID currentId, Instant now, String reason);
}
