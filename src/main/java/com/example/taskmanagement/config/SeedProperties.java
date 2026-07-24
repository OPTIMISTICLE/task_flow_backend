package com.example.taskmanagement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.seed")
public record SeedProperties(boolean enabled, String password) {
}
