package com.example.taskmanagement.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_outbox")
public class EmailOutbox {
    @Id
    private UUID id;
    @Column(nullable = false, length = 254)
    private String recipient;
    @Column(name = "template_name", nullable = false, length = 50)
    private String templateName;
    @Column(nullable = false, length = 255)
    private String subject;
    @Column(name = "text_body", nullable = false, columnDefinition = "TEXT")
    private String textBody;
    @Column(name = "html_body", nullable = false, columnDefinition = "TEXT")
    private String htmlBody;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailOutboxStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "provider_message_id", length = 120)
    private String providerMessageId;
    @Column(name = "last_error", length = 1000)
    private String lastError;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "sent_at")
    private Instant sentAt;

    protected EmailOutbox() {
    }

    public EmailOutbox(String recipient, String templateName, String subject,
                       String textBody, String htmlBody, Instant now) {
        this.id = UUID.randomUUID();
        this.recipient = recipient;
        this.templateName = templateName;
        this.subject = subject;
        this.textBody = textBody;
        this.htmlBody = htmlBody;
        this.status = EmailOutboxStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public void sending() { status = EmailOutboxStatus.SENDING; attempts++; }
    public void sent(String providerId, Instant now) {
        status = EmailOutboxStatus.SENT;
        providerMessageId = providerId;
        sentAt = now;
        lastError = null;
    }
    public void retry(String error, Instant next) {
        status = EmailOutboxStatus.PENDING;
        lastError = safe(error);
        nextAttemptAt = next;
    }
    public void failed(String error) {
        status = EmailOutboxStatus.FAILED;
        lastError = safe(error);
    }

    public UUID getId() { return id; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getTextBody() { return textBody; }
    public String getHtmlBody() { return htmlBody; }
    public int getAttempts() { return attempts; }
    public EmailOutboxStatus getStatus() { return status; }

    private String safe(String value) {
        if (value == null) return null;
        return value.substring(0, Math.min(value.length(), 1000));
    }
}
