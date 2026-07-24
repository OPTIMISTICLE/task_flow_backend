package com.example.taskmanagement.email;

import com.example.taskmanagement.config.MailProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailOutboxDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");
    private final EmailOutboxRepository outboxRepository = mock(EmailOutboxRepository.class);
    private final OutboundEmailSender sender = mock(OutboundEmailSender.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void marksDeliveredMessagesAsSentWithTheProviderId() {
        EmailOutbox message = message();
        when(outboxRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(EmailOutboxStatus.PENDING), eq(NOW), any(Pageable.class))).thenReturn(List.of(message));
        when(sender.send(message)).thenReturn("gmail-123");

        dispatcher(enabledProperties()).dispatch();

        assertThat(message.getStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(message.getProviderMessageId()).isEqualTo("gmail-123");
        assertThat(message.getAttempts()).isEqualTo(1);
    }

    @Test
    void schedulesExponentialRetryAfterADeliveryFailure() {
        EmailOutbox message = message();
        when(outboxRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(EmailOutboxStatus.PENDING), eq(NOW), any(Pageable.class))).thenReturn(List.of(message));
        when(sender.send(message)).thenThrow(new EmailDeliveryException("temporary Gmail failure"));

        dispatcher(enabledProperties()).dispatch();

        assertThat(message.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(message.getAttempts()).isEqualTo(1);
        assertThat(message.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertThat(message.getLastError()).isEqualTo("temporary Gmail failure");
    }

    @Test
    void marksTheFifthFailedAttemptAsFinal() {
        EmailOutbox message = message();
        for (int attempt = 0; attempt < 4; attempt++) {
            message.sending();
        }
        when(outboxRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(EmailOutboxStatus.PENDING), eq(NOW), any(Pageable.class))).thenReturn(List.of(message));
        when(sender.send(message)).thenThrow(new EmailDeliveryException("permanent Gmail failure"));

        dispatcher(enabledProperties()).dispatch();

        assertThat(message.getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
        assertThat(message.getAttempts()).isEqualTo(5);
        assertThat(message.getLastError()).isEqualTo("permanent Gmail failure");
    }

    @Test
    void doesNothingWhenMailIsDisabled() {
        dispatcher(new MailProperties(false, "TaskFlow", "", "", "", "", "https://example.com"))
                .dispatch();

        verify(outboxRepository, never()).findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                any(), any(), any());
        verify(sender, never()).send(any());
    }

    private EmailOutboxDispatcher dispatcher(MailProperties properties) {
        return new EmailOutboxDispatcher(outboxRepository, sender, properties, clock);
    }

    private MailProperties enabledProperties() {
        return new MailProperties(true, "TaskFlow", "sender@gmail.com", "client-id",
                "client-secret", "refresh-token", "https://taskflow.example");
    }

    private EmailOutbox message() {
        return new EmailOutbox("person@example.com", "NOTICE", "Notice", "Text", "<p>Text</p>", NOW);
    }
}
