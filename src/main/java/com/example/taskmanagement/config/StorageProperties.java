package com.example.taskmanagement.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.storage")
public record StorageProperties(@NotNull Provider provider, @NotBlank String directory, Supabase supabase) {

    public enum Provider {
        LOCAL,
        SUPABASE
    }

    public record Supabase(String url, String secretKey, String bucket) {
    }
}
