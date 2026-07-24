package com.example.taskmanagement.task;

import com.example.taskmanagement.IntegrationTestSupport;
import com.example.taskmanagement.user.User;
import com.example.taskmanagement.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskWorkflowIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void managerCreatesWorkerExecutesAndManagerSeesCompletion() throws Exception {
        Cookie manager = login("manager@company.local");
        Cookie worker1 = login("worker1@company.local");
        Cookie worker2 = login("worker2@company.local");
        User assignee = userRepository.findByEmailIgnoreCase("worker1@company.local").orElseThrow();

        String taskId = objectMapper.readTree(mockMvc.perform(post("/api/tasks")
                        .cookie(manager).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"title":"Prepare weekly report","description":"Include delivery risks.","assigneeId":"%s","dueDate":"%s"}
                                """.formatted(assignee.getId(), Instant.now().plusSeconds(86_400))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.progressStatus").value("ASSIGNED"))
                .andExpect(jsonPath("$.effectiveStatus").value("ASSIGNED"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/tasks").cookie(worker1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(taskId));

        mockMvc.perform(get("/api/tasks").cookie(worker2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(patch("/api/tasks/{id}/status", taskId)
                        .cookie(worker2).with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/tasks/{id}/status", taskId)
                        .cookie(worker1).with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveStatus").value("IN_PROGRESS"));

        mockMvc.perform(patch("/api/tasks/{id}/status", taskId)
                        .cookie(worker1).with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        mockMvc.perform(get("/api/tasks/{id}", taskId).cookie(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveStatus").value("COMPLETED"));

        mockMvc.perform(patch("/api/tasks/{id}/status", taskId)
                        .cookie(worker1).with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void overdueIsCalculatedAndFilterableWithoutChangingStoredProgress() throws Exception {
        Cookie worker = login("worker1@company.local");
        User manager = userRepository.findByEmailIgnoreCase("manager@company.local").orElseThrow();
        User assignee = userRepository.findByEmailIgnoreCase("worker1@company.local").orElseThrow();
        TaskEntity overdue = taskRepository.saveAndFlush(new TaskEntity(
                "Past deadline", null, Instant.now().minusSeconds(60), manager, assignee));

        mockMvc.perform(get("/api/tasks?status=OVERDUE").cookie(worker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(overdue.getId().toString()))
                .andExpect(jsonPath("$[0].progressStatus").value("ASSIGNED"))
                .andExpect(jsonPath("$[0].effectiveStatus").value("OVERDUE"));
    }

    @Test
    void roleAndAssignmentTamperingAreRejected() throws Exception {
        Cookie worker = login("worker1@company.local");
        Cookie manager = login("manager@company.local");
        User managerUser = userRepository.findByEmailIgnoreCase("manager@company.local").orElseThrow();

        mockMvc.perform(post("/api/tasks").cookie(worker).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"title":"Unauthorized task","assigneeId":"%s"}
                                """.formatted(managerUser.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/tasks").cookie(manager).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"title":"Invalid assignment","assigneeId":"%s"}
                                """.formatted(managerUser.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The assignee must be an active worker."));
    }
}
