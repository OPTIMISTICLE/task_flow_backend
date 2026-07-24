package com.example.taskmanagement.email;

import com.example.taskmanagement.config.MailProperties;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

@Component
public class GmailApiEmailSender implements OutboundEmailSender {
    private static final Session MIME_SESSION = Session.getInstance(new Properties());

    private final MailProperties properties;
    private final GmailAccessTokenProvider tokens;
    private final RestClient client;
    private final Clock clock;

    public GmailApiEmailSender(MailProperties properties, GmailAccessTokenProvider tokens,
                               @Qualifier("gmailApiClient") RestClient client, Clock clock) {
        this.properties = properties;
        this.tokens = tokens;
        this.client = client;
        this.clock = clock;
    }

    @Override
    public String send(EmailOutbox message) {
        String raw = encode(message);
        String accessToken = tokens.accessToken();
        try {
            return deliver(raw, accessToken);
        } catch (HttpClientErrorException.Unauthorized exception) {
            tokens.invalidate(accessToken);
            return deliver(raw, tokens.accessToken());
        }
    }

    private String deliver(String raw, String accessToken) {
        GmailMessageResponse response = client.post()
                .uri("/gmail/v1/users/me/messages/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("raw", raw))
                .retrieve()
                .body(GmailMessageResponse.class);
        if (response == null || response.id() == null || response.id().isBlank()) {
            throw new EmailDeliveryException("Gmail API returned an incomplete send response");
        }
        return response.id();
    }

    private String encode(EmailOutbox outbox) {
        try {
            MimeMessage message = new MimeMessage(MIME_SESSION);
            message.setFrom(new InternetAddress(properties.gmailSenderEmail(), properties.fromName(),
                    StandardCharsets.UTF_8.name()));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(outbox.getRecipient(), true));
            message.setSubject(outbox.getSubject(), StandardCharsets.UTF_8.name());
            message.setSentDate(Date.from(clock.instant()));
            message.setHeader("Auto-Submitted", "auto-generated");
            message.setHeader("X-Auto-Response-Suppress", "All");

            MimeBodyPart text = new MimeBodyPart();
            text.setText(outbox.getTextBody(), StandardCharsets.UTF_8.name());
            MimeBodyPart html = new MimeBodyPart();
            html.setContent(outbox.getHtmlBody(), "text/html; charset=UTF-8");
            MimeMultipart alternatives = new MimeMultipart("alternative");
            alternatives.addBodyPart(text);
            alternatives.addBodyPart(html);
            message.setContent(alternatives);
            message.saveChanges();
            message.setHeader("Message-ID", "<" + outbox.getId() + "@taskflow.invalid>");

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            message.writeTo(output);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
        } catch (MessagingException | IOException exception) {
            throw new EmailDeliveryException("Unable to create the Gmail MIME message", exception);
        }
    }

    record GmailMessageResponse(String id) {
    }
}
