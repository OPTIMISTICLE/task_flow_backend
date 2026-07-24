package com.example.taskmanagement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.mail")
public record MailProperties(
        boolean enabled,
        String fromName,
        String gmailSenderEmail,
        String gmailClientId,
        String gmailClientSecret,
        String gmailRefreshToken,
        String frontendOrigin
) {
}
