package com.example.taskmanagement.auth;

import com.example.taskmanagement.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mfa_totp_credentials")
public class MfaTotpCredential {
    @Id
    @Column(name = "user_id")
    private UUID userId;
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;
    @Column(name = "encrypted_secret", nullable = false, length = 512)
    private String encryptedSecret;
    @Column(nullable = false, length = 64)
    private String nonce;
    @Column(name = "last_used_step")
    private Long lastUsedStep;
    @Column(name = "enabled_at")
    private Instant enabledAt;

    protected MfaTotpCredential() {
    }

    public MfaTotpCredential(User user, String encryptedSecret, String nonce) {
        this.user = user;
        this.encryptedSecret = encryptedSecret;
        this.nonce = nonce;
    }

    public void recordStep(long step) { lastUsedStep = step; }
    public void enable(Instant now) { enabledAt = now; }
    public boolean isEnabled() { return enabledAt != null; }
    public User getUser() { return user; }
    public String getEncryptedSecret() { return encryptedSecret; }
    public String getNonce() { return nonce; }
    public Long getLastUsedStep() { return lastUsedStep; }
    public Instant getEnabledAt() { return enabledAt; }
}
