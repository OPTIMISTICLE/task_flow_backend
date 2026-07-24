package com.example.taskmanagement.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class User {
    @Id
    private UUID id;
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(name = "pending_email", length = 254)
    private String pendingEmail;
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;
    @Column(name = "password_hash", length = 100)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private UserAccountStatus accountStatus;
    @Column(name = "job_title", length = 120)
    private String jobTitle;
    @Column(length = 120)
    private String department;
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;
    @Column(name = "auth_version", nullable = false)
    private long authVersion;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(nullable = false)
    private long version;

    protected User() {
    }

    public User(String firstName, String lastName, String email, String passwordHash, UserRole role) {
        this(firstName, lastName, email, passwordHash, role, UserAccountStatus.ACTIVE,
                null, null, null, false, Instant.now());
        this.emailVerifiedAt = createdAt;
    }

    public User(String firstName, String lastName, String email, String passwordHash, UserRole role,
                UserAccountStatus accountStatus, String jobTitle, String department, String phoneNumber,
                boolean mustChangePassword, Instant now) {
        this.id = UUID.randomUUID();
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.role = role;
        this.accountStatus = accountStatus;
        this.jobTitle = jobTitle;
        this.department = department;
        this.phoneNumber = phoneNumber;
        this.mustChangePassword = mustChangePassword;
        this.createdAt = now;
        this.updatedAt = now;
        if (accountStatus == UserAccountStatus.ACTIVE) {
            this.emailVerifiedAt = now;
        }
    }

    public void updateProfile(String firstName, String lastName, String jobTitle,
                              String department, String phoneNumber, Instant now) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.jobTitle = jobTitle;
        this.department = department;
        this.phoneNumber = phoneNumber;
        this.updatedAt = now;
    }

    public void requestEmailChange(String value, Instant now) {
        this.pendingEmail = normalizeEmail(value);
        this.updatedAt = now;
    }

    public void replaceInvitationEmail(String value, Instant now) {
        if (accountStatus != UserAccountStatus.PENDING) {
            throw new IllegalStateException("Only pending accounts can replace an invitation email");
        }
        this.email = normalizeEmail(value);
        this.pendingEmail = null;
        this.emailVerifiedAt = null;
        invalidateAuthentication(now);
    }

    public void clearPendingEmail(Instant now) {
        this.pendingEmail = null;
        this.updatedAt = now;
    }

    public void confirmPendingEmail(Instant now) {
        if (pendingEmail != null) {
            email = pendingEmail;
            pendingEmail = null;
            emailVerifiedAt = now;
            invalidateAuthentication(now);
        }
    }

    public void activate(String hash, Instant now) {
        passwordHash = hash;
        accountStatus = UserAccountStatus.ACTIVE;
        emailVerifiedAt = now;
        mustChangePassword = false;
        invalidateAuthentication(now);
    }

    public void changeActive(boolean value, Instant now) {
        accountStatus = value ? UserAccountStatus.ACTIVE : UserAccountStatus.INACTIVE;
        invalidateAuthentication(now);
    }

    public void markPending(Instant now) {
        accountStatus = UserAccountStatus.PENDING;
        invalidateAuthentication(now);
    }

    public void changeRole(UserRole value, Instant now) {
        role = value;
        invalidateAuthentication(now);
    }

    public void replacePassword(String hash, boolean requireChange, Instant now) {
        passwordHash = hash;
        mustChangePassword = requireChange;
        invalidateAuthentication(now);
    }

    public void invalidateAuthentication(Instant now) {
        authVersion++;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPendingEmail() { return pendingEmail; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
    public UserAccountStatus getAccountStatus() { return accountStatus; }
    public boolean isActive() { return accountStatus == UserAccountStatus.ACTIVE; }
    public boolean hasPassword() { return passwordHash != null && !passwordHash.isBlank(); }
    public String getJobTitle() { return jobTitle; }
    public String getDepartment() { return department; }
    public String getPhoneNumber() { return phoneNumber; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public long getAuthVersion() { return authVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public String getDisplayName() { return firstName + " " + lastName; }

    private static String normalizeEmail(String value) {
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}
