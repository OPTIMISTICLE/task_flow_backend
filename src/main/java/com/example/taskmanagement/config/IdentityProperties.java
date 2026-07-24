package com.example.taskmanagement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("app.identity")
public record IdentityProperties(
        Duration sessionTtl,
        Duration invitationTtl,
        Duration passwordResetTtl,
        Duration loginChallengeTtl,
        String mfaEncryptionKey
) {
}
