package com.example.taskmanagement.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    boolean existsByAction(String action);

    @Query("""
            select event from AuditEvent event
            where event.actorId = :userId
               or (event.resourceType = 'USER' and event.resourceId = :resourceId)
            order by event.occurredAt desc
            """)
    Page<AuditEvent> findUserTimeline(@Param("userId") UUID userId,
                                      @Param("resourceId") String resourceId,
                                      Pageable pageable);
}
