package com.example.taskmanagement.audit;

import com.example.taskmanagement.auth.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(AuthenticatedUser actor, String action, String resourceType, UUID resourceId, String details) {
        record(actor == null ? null : actor.id(), actor == null ? null : actor.getUsername(),
                action, resourceType, resourceId, "SUCCESS", details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(String actorEmail, String action, String resourceType, UUID resourceId, String details) {
        record(null, actorEmail, action, resourceType, resourceId, "FAILURE", details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void denied(AuthenticatedUser actor, String action, String details) {
        record(actor == null ? null : actor.id(), actor == null ? null : actor.getUsername(),
                action, null, null, "DENIED", details);
    }

    private void record(UUID actorId, String actorEmail, String action, String resourceType,
                        UUID resourceId, String outcome, String details) {
        String safeDetails = details == null ? null : details.substring(0, Math.min(details.length(), 1000));
        try {
            repository.save(new AuditEvent(actorId, actorEmail, action, resourceType,
                    resourceId == null ? null : resourceId.toString(), outcome, safeDetails));
            log.info("audit action={} outcome={} actor={} resourceType={} resourceId={}",
                    action, outcome, actorEmail, resourceType, resourceId);
        } catch (DataAccessException exception) {
            log.error("Could not persist audit event action={} outcome={}", action, outcome, exception);
        }
    }
}
