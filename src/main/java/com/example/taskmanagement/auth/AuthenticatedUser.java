package com.example.taskmanagement.auth;

import com.example.taskmanagement.user.User;
import com.example.taskmanagement.user.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String email,
        String passwordHash,
        String firstName,
        String lastName,
        UserRole role,
        boolean active,
        boolean mustChangePassword,
        long authVersion
) implements UserDetails {

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getPasswordHash(),
                user.getFirstName(), user.getLastName(), user.getRole(), user.isActive(),
                user.isMustChangePassword(), user.getAuthVersion());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    public String displayName() {
        return firstName + " " + lastName;
    }
}
