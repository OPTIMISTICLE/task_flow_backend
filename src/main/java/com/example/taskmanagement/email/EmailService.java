package com.example.taskmanagement.email;

import com.example.taskmanagement.config.MailProperties;
import com.example.taskmanagement.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;

@Service
public class EmailService {
    private final EmailOutboxRepository outbox;
    private final MailProperties properties;
    private final Clock clock;

    public EmailService(EmailOutboxRepository outbox, MailProperties properties, Clock clock) {
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void invitation(User user, String token) {
        String link = link("/accept-invitation", token);
        queue(user.getEmail(), "invitation", "You are invited to TaskFlow",
                "Hello " + user.getDisplayName() + ",\n\nSet your password and activate your account: " + link
                        + "\n\nThis link expires in 24 hours.",
                paragraph("Hello " + user.getDisplayName() + ",")
                        + paragraph("You have been invited to TaskFlow.") + action("Accept invitation", link)
                        + paragraph("This link expires in 24 hours."));
    }

    @Transactional
    public void passwordReset(User user, String token) {
        String link = link("/reset-password", token);
        queue(user.getEmail(), "password-reset", "Reset your TaskFlow password",
                "Reset your password: " + link + "\n\nThis link expires in 30 minutes.",
                paragraph("A password reset was requested for your TaskFlow account.")
                        + action("Reset password", link) + paragraph("This link expires in 30 minutes."));
    }

    @Transactional
    public void emailChange(User user, String pendingEmail, String token) {
        String link = link("/confirm-email", token);
        queue(pendingEmail, "email-change", "Confirm your TaskFlow email address",
                "Confirm your new email address: " + link,
                paragraph("Confirm this email address for your TaskFlow account.")
                        + action("Confirm email", link));
    }

    @Transactional
    public void securityNotice(User user, String subject, String detail) {
        queue(user.getEmail(), "security-notice", subject, detail,
                paragraph(detail) + paragraph("If this was not you, contact your administrator immediately."));
    }

    private String link(String path, String token) {
        return UriComponentsBuilder.fromUriString(properties.frontendOrigin())
                .path(path).queryParam("token", token).build().toUriString();
    }

    private void queue(String recipient, String template, String subject, String text, String html) {
        outbox.save(new EmailOutbox(recipient, template, subject, text,
                "<!doctype html><html><body>" + html + "</body></html>", clock.instant()));
    }

    private String paragraph(String value) {
        return "<p>" + HtmlUtils.htmlEscape(value) + "</p>";
    }

    private String action(String label, String link) {
        return "<p><a href=\"" + HtmlUtils.htmlEscape(link) + "\">" + HtmlUtils.htmlEscape(label) + "</a></p>";
    }
}
