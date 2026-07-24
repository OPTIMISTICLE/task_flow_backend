package com.example.taskmanagement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.mail")
public record MailProperties(
        boolean enabled,
        String apiKey,
        String from,
        String frontendOrigin
) {
}
