package com.example.taskmanagement.email;

import com.example.taskmanagement.config.MailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class EmailOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(EmailOutboxDispatcher.class);
    private final EmailOutboxRepository outbox;
    private final OutboundEmailSender sender;
    private final MailProperties properties;
    private final Clock clock;

    public EmailOutboxDispatcher(EmailOutboxRepository outbox, OutboundEmailSender sender,
                                 MailProperties properties, Clock clock) {
        this.outbox = outbox;
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.mail.dispatch-delay:10000}")
    @Transactional
    public void dispatch() {
        if (!properties.enabled()) {
            return;
        }
        Instant now = clock.instant();
        for (EmailOutbox message : outbox.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                EmailOutboxStatus.PENDING, now, PageRequest.of(0, 20))) {
            deliver(message, now);
        }
    }

    private void deliver(EmailOutbox message, Instant now) {
        message.sending();
        try {
            message.sent(sender.send(message), now);
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
}
