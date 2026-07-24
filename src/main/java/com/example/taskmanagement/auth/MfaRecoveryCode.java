package com.example.taskmanagement.auth;

import com.example.taskmanagement.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mfa_recovery_codes")
public class MfaRecoveryCode {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "used_at")
    private Instant usedAt;

    protected MfaRecoveryCode() {
    }

    public MfaRecoveryCode(User user, String codeHash, Instant now) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.codeHash = codeHash;
        this.createdAt = now;
    }

    public boolean isUnused() { return usedAt == null; }
    public void use(Instant now) { usedAt = now; }
    public String getCodeHash() { return codeHash; }
}
