package com.example.taskmanagement.email;

import com.example.taskmanagement.config.MailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class EmailOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(EmailOutboxDispatcher.class);
    private final EmailOutboxRepository outbox;
    private final MailProperties properties;
    private final Clock clock;
    private final RestClient client = RestClient.create("https://api.resend.com");

    public EmailOutboxDispatcher(EmailOutboxRepository outbox, MailProperties properties, Clock clock) {
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.mail.dispatch-delay:10000}")
    @Transactional
    public void dispatch() {
        if (!properties.enabled()) {
            return;
        }
        validateConfiguration();
        Instant now = clock.instant();
        for (EmailOutbox message : outbox.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                EmailOutboxStatus.PENDING, now, PageRequest.of(0, 20))) {
            deliver(message, now);
        }
    }

    private void deliver(EmailOutbox message, Instant now) {
        message.sending();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post().uri("/emails")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("Idempotency-Key", message.getId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("from", properties.from(), "to", new String[]{message.getRecipient()},
                            "subject", message.getSubject(), "text", message.getTextBody(),
                            "html", message.getHtmlBody()))
                    .retrieve().body(Map.class);
            message.sent(response == null ? null : String.valueOf(response.get("id")), now);
        } catch (RuntimeException exception) {
            if (message.getAttempts() >= 5) {
                message.failed(exception.getMessage());
            } else {
                long minutes = 1L << Math.min(message.getAttempts() - 1, 4);
                message.retry(exception.getMessage(), now.plus(Duration.ofMinutes(minutes)));
            }
            log.warn("Email delivery failed for outbox message {}: {}", message.getId(),
                    exception.getClass().getSimpleName());
        }
    }

    private void validateConfiguration() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()
                || properties.from() == null || properties.from().isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY and MAIL_FROM are required when mail is enabled");
        }
    }
}
