package com.example.taskmanagement.email;

public interface OutboundEmailSender {
    String send(EmailOutbox message);
}
