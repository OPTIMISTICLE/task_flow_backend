package com.example.taskmanagement.email;

import com.example.taskmanagement.config.MailProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GmailApiEmailSenderTest {
    private final GmailAccessTokenProvider tokens = mock(GmailAccessTokenProvider.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private GmailApiEmailSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gmail.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new GmailApiEmailSender(properties(), tokens, builder.build(),
                Clock.fixed(Instant.parse("2026-07-24T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void sendsAUtf8MultipartMessageAndReturnsTheGmailId() {
        when(tokens.accessToken()).thenReturn("access-token");
        EmailOutbox outbox = new EmailOutbox("person@example.com", "INVITATION",
                "Bienvenue à TaskFlow", "Plain invitation", "<p>HTML invitation</p>",
                Instant.parse("2026-07-24T12:00:00Z"));

        server.expect(once(), requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(request -> assertMimeRequest((MockClientHttpRequest) request, outbox))
                .andRespond(withSuccess("{\"id\":\"gmail-message-123\"}", MediaType.APPLICATION_JSON));

        assertThat(sender.send(outbox)).isEqualTo("gmail-message-123");
        server.verify();
    }

    @Test
    void refreshesOnceWhenGmailRejectsTheCachedAccessToken() {
        when(tokens.accessToken()).thenReturn("expired-token", "fresh-token");
        EmailOutbox outbox = new EmailOutbox("person@example.com", "NOTICE", "Notice",
                "Text", "<p>Text</p>", Instant.parse("2026-07-24T12:00:00Z"));

        server.expect(once(), requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(once(), requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer fresh-token"))
                .andRespond(withSuccess("{\"id\":\"gmail-message-456\"}", MediaType.APPLICATION_JSON));

        assertThat(sender.send(outbox)).isEqualTo("gmail-message-456");
        verify(tokens).invalidate("expired-token");
        server.verify();
    }

    private void assertMimeRequest(MockClientHttpRequest request, EmailOutbox outbox) {
        try {
            JsonNode body = objectMapper.readTree(request.getBodyAsString());
            byte[] decoded = Base64.getUrlDecoder().decode(body.required("raw").asText());
            MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(decoded));

            assertThat(mime.getFrom()[0].toString()).isEqualTo("TaskFlow <sender@gmail.com>");
            assertThat(mime.getAllRecipients()[0].toString()).isEqualTo("person@example.com");
            assertThat(mime.getSubject()).isEqualTo("Bienvenue à TaskFlow");
            assertThat(mime.getHeader("Message-ID", null))
                    .isEqualTo("<" + outbox.getId() + "@taskflow.invalid>");
            assertThat(mime.getHeader("Auto-Submitted", null)).isEqualTo("auto-generated");

            MimeMultipart alternatives = (MimeMultipart) mime.getContent();
            assertThat(alternatives.getCount()).isEqualTo(2);
            assertThat(String.valueOf(alternatives.getBodyPart(0).getContent())).contains("Plain invitation");
            assertThat(String.valueOf(alternatives.getBodyPart(1).getContent())).contains("HTML invitation");
        } catch (Exception exception) {
            throw new AssertionError("Unable to inspect Gmail request MIME", exception);
        }
    }

    private MailProperties properties() {
        return new MailProperties(true, "TaskFlow", "sender@gmail.com", "client-id",
                "client-secret", "refresh-token", "https://taskflow.example");
    }
}
