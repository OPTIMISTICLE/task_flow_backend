package com.example.taskmanagement.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
        @Positive long loginCapacity,
        @Positive long loginRefill,
        @NotNull Duration loginPeriod,
        @Positive long apiCapacity,
        @Positive long apiRefill,
        @NotNull Duration apiPeriod
) {
}
