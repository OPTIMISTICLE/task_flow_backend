package com.example.taskmanagement.task;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    boolean existsByCreatorId(UUID creatorId);

    boolean existsByAssigneeId(UUID assigneeId);

    boolean existsByCreatorIdAndProgressStatusNot(UUID creatorId, TaskProgressStatus status);

    boolean existsByAssigneeIdAndProgressStatusNot(UUID assigneeId, TaskProgressStatus status);

    @EntityGraph(attributePaths = {"creator", "assignee"})
    List<TaskEntity> findByCreatorIdOrderByCreatedAtDesc(UUID creatorId);

    @EntityGraph(attributePaths = {"creator", "assignee"})
    List<TaskEntity> findByAssigneeIdOrderByCreatedAtDesc(UUID assigneeId);

    @Override
    @EntityGraph(attributePaths = {"creator", "assignee"})
    Optional<TaskEntity> findById(UUID id);
}
