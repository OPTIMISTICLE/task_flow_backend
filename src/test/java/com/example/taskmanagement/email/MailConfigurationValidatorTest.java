package com.example.taskmanagement.email;

import com.example.taskmanagement.config.MailProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailConfigurationValidatorTest {

    @Test
    void acceptsCompleteGmailConfiguration() {
        MailProperties properties = new MailProperties(true, "TaskFlow", "sender@gmail.com",
                "client-id", "client-secret", "refresh-token", "https://taskflow.example");

        assertThatCode(() -> new MailConfigurationValidator(properties).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingCredentialsWhenMailIsEnabled() {
        MailProperties properties = new MailProperties(true, "TaskFlow", "sender@gmail.com",
                "client-id", "", "refresh-token", "https://taskflow.example");

        assertThatThrownBy(() -> new MailConfigurationValidator(properties).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("GMAIL_CLIENT_SECRET is required when mail is enabled");
    }

    @Test
    void permitsEmptyCredentialsWhenMailIsDisabled() {
        MailProperties properties = new MailProperties(false, "TaskFlow", "", "", "", "",
                "https://taskflow.example");

        assertThatCode(() -> new MailConfigurationValidator(properties).afterPropertiesSet())
                .doesNotThrowAnyException();
    }
}
