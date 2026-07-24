package com.example.taskmanagement.email;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {
    Optional<EmailOutbox> findTopByRecipientOrderByCreatedAtDesc(String recipient);
    List<EmailOutbox> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            EmailOutboxStatus status, Instant now, Pageable pageable);
}
