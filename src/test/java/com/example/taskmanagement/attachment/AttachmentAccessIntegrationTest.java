package com.example.taskmanagement.attachment;

import com.example.taskmanagement.IntegrationTestSupport;
import com.example.taskmanagement.user.User;
import com.example.taskmanagement.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttachmentAccessIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void authorizedUsersCanUploadAndDownloadButOtherWorkersCannot() throws Exception {
        Cookie manager = login("manager@company.local");
        Cookie worker1 = login("worker1@company.local");
        Cookie worker2 = login("worker2@company.local");
        User assignee = userRepository.findByEmailIgnoreCase("worker1@company.local").orElseThrow();

        JsonNode task = objectMapper.readTree(mockMvc.perform(post("/api/tasks")
                        .cookie(manager).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"title":"Review artifact","assigneeId":"%s"}
                                """.formatted(assignee.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String taskId = task.get("id").asText();

        MockMultipartFile file = new MockMultipartFile(
                "file", "result.txt", "text/plain", "verified output".getBytes());
        JsonNode attachment = objectMapper.readTree(mockMvc.perform(multipart("/api/tasks/{taskId}/attachments", taskId)
                        .file(file).cookie(worker1).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String attachmentId = attachment.get("id").asText();

        mockMvc.perform(get("/api/tasks/{taskId}/attachments/{attachmentId}", taskId, attachmentId)
                        .cookie(manager))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("result.txt")))
                .andExpect(content().bytes("verified output".getBytes()));

        mockMvc.perform(get("/api/tasks/{taskId}/attachments/{attachmentId}", taskId, attachmentId)
                        .cookie(worker2))
                .andExpect(status().isForbidden());
    }
}
