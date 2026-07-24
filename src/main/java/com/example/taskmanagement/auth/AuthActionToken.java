package com.example.taskmanagement.auth;

import com.example.taskmanagement.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_action_tokens")
public class AuthActionToken {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuthTokenPurpose purpose;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "pending_email", length = 254)
    private String pendingEmail;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "used_at")
    private Instant usedAt;

    protected AuthActionToken() {
    }

    public AuthActionToken(User user, AuthTokenPurpose purpose, String tokenHash, String pendingEmail,
                           Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.pendingEmail = pendingEmail;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) { return usedAt == null && expiresAt.isAfter(now); }
    public void use(Instant now) { usedAt = now; }
    public User getUser() { return user; }
    public AuthTokenPurpose getPurpose() { return purpose; }
    public String getPendingEmail() { return pendingEmail; }
    public Instant getExpiresAt() { return expiresAt; }
}
