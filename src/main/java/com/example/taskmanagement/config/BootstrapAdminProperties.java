package com.example.taskmanagement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.bootstrap-admin")
public record BootstrapAdminProperties(
        String email,
        String password,
        String firstName,
        String lastName
) {
    public boolean complete() {
        return has(email) && has(password) && has(firstName) && has(lastName);
    }

    private boolean has(String value) {
        return value != null && !value.isBlank();
    }
}
