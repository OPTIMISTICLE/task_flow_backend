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

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private boolean active;

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
        this(firstName, lastName, email, passwordHash, role, null, null, null, false, Instant.now());
    }

    public User(String firstName, String lastName, String email, String passwordHash, UserRole role,
                String jobTitle, String department, String phoneNumber, boolean mustChangePassword, Instant now) {
        this.id = UUID.randomUUID();
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = true;
        this.jobTitle = jobTitle;
        this.department = department;
        this.phoneNumber = phoneNumber;
        this.mustChangePassword = mustChangePassword;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateProfile(String firstName, String lastName, String email, String jobTitle,
                              String department, String phoneNumber, Instant now) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = normalizeEmail(email);
        this.jobTitle = jobTitle;
        this.department = department;
        this.phoneNumber = phoneNumber;
        this.updatedAt = now;
    }

    public void changeRole(UserRole role, Instant now) {
        this.role = role;
        this.authVersion++;
        this.updatedAt = now;
    }

    public void changeActive(boolean active, Instant now) {
        this.active = active;
        this.authVersion++;
        this.updatedAt = now;
    }

    public void replacePassword(String passwordHash, boolean mustChangePassword, Instant now) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = mustChangePassword;
        this.authVersion++;
        this.updatedAt = now;
    }

    private String normalizeEmail(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getDepartment() {
        return department;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public long getAuthVersion() {
        return authVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public String getDisplayName() {
        return firstName + " " + lastName;
    }
}
