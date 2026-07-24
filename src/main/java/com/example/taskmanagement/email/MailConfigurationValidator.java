package com.example.taskmanagement.email;

import com.example.taskmanagement.config.MailProperties;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class MailConfigurationValidator implements InitializingBean {
    private final MailProperties properties;

    public MailConfigurationValidator(MailProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        if (!properties.enabled()) {
            return;
        }
        require(properties.fromName(), "MAIL_FROM_NAME");
        require(properties.gmailSenderEmail(), "GMAIL_SENDER_EMAIL");
        require(properties.gmailClientId(), "GMAIL_CLIENT_ID");
        require(properties.gmailClientSecret(), "GMAIL_CLIENT_SECRET");
        require(properties.gmailRefreshToken(), "GMAIL_REFRESH_TOKEN");
        try {
            InternetAddress address = new InternetAddress(properties.gmailSenderEmail(), true);
            address.validate();
        } catch (AddressException exception) {
            throw new IllegalStateException("GMAIL_SENDER_EMAIL must be a valid email address", exception);
        }
    }

    private void require(String value, String environmentName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentName + " is required when mail is enabled");
        }
    }
}
