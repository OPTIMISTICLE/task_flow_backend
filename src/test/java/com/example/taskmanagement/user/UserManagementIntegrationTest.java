package com.example.taskmanagement.user;

import com.example.taskmanagement.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.taskmanagement.email.EmailOutboxRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserManagementIntegrationTest extends IntegrationTestSupport {

    private static final String BOOTSTRAP_PASSWORD = "TestAdminPassword123!";
    private static final String ADMIN_PASSWORD = "EstablishedAdminPassword123!";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @BeforeEach
    void resetAdministratorCredentials() {
        User admin = userRepository.findByEmailIgnoreCase("admin@company.local").orElseThrow();
        admin.replacePassword(passwordEncoder.encode(BOOTSTRAP_PASSWORD), true, Instant.now());
        userRepository.saveAndFlush(admin);
    }

    @Test
    void administratorCreatesSearchesUpdatesAndAuditsAUser() throws Exception {
        Cookie admin = establishedAdminCookie();
        String email = "new.worker." + UUID.randomUUID() + "@company.local";

        var createdResponse = mockMvc.perform(post("/api/admin/users")
                        .with(csrf()).cookie(admin).contentType("application/json")
                        .content(createUserJson(email, "WORKER")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn();
        JsonNode created = objectMapper.readTree(createdResponse.getResponse().getContentAsString());
        assertFalse(created.has("temporaryPassword"));
        String userId = created.get("id").asText();
        long version = created.get("version").asLong();

        mockMvc.perform(get("/api/admin/users")
                        .cookie(admin).param("query", email.toUpperCase()).param("role", "WORKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(userId));

        mockMvc.perform(patch("/api/admin/users/{id}", userId)
                        .with(csrf()).cookie(admin).contentType("application/json")
                        .content("""
                                {
                                  "version": %d,
                                  "firstName": "Nora",
                                  "lastName": "Worker",
                                  "email": "%s",
                                  "jobTitle": "Operations Analyst",
                                  "department": "Operations",
                                  "phoneNumber": "+14155552671",
                                  "role": "WORKER"
                                }
                                """.formatted(version, email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Operations Analyst"))
                .andExpect(jsonPath("$.department").value("Operations"));

        mockMvc.perform(get("/api/admin/users/{id}/audit", userId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)));
    }

    @Test
    void invitationActivatesTheAccountAndCannotBeReused() throws Exception {
        Cookie admin = establishedAdminCookie();
        String email = "temporary." + UUID.randomUUID() + "@company.local";
        var response = mockMvc.perform(post("/api/admin/users")
                        .with(csrf()).cookie(admin).contentType("application/json")
                        .content(createUserJson(email, "WORKER")))
                .andExpect(status().isCreated())
                .andReturn();
        String token = invitationToken(email);
        String password = "A new private passphrase 2026";
        mockMvc.perform(post("/api/auth/invitations/accept").with(csrf()).contentType("application/json")
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(token, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        Cookie cookie = loginWithPassword(email, password, false);
        mockMvc.perform(get("/api/tasks").cookie(cookie)).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/invitations/accept").with(csrf()).contentType("application/json")
                        .content("""
                                {"token":"%s","password":"Another private passphrase 2026"}
                                """.formatted(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roleBoundariesAndLastAdministratorSafeguardsAreEnforced() throws Exception {
        Cookie admin = establishedAdminCookie();
        Cookie manager = login("manager@company.local");
        User adminUser = userRepository.findByEmailIgnoreCase("admin@company.local").orElseThrow();

        mockMvc.perform(get("/api/admin/users").cookie(manager))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tasks").cookie(admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/users/{id}/deactivate", adminUser.getId())
                        .with(csrf()).cookie(admin).contentType("application/json")
                        .content("{\"version\":" + adminUser.getVersion() + "}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/admin/users/{id}/audit", adminUser.getId()).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].action", hasItem("USER_DEACTIVATE_DENIED")));
    }

    @Test
    void unfinishedAssignmentsBlockDeactivationAndHistoryBlocksRoleChanges() throws Exception {
        Cookie admin = establishedAdminCookie();
        Cookie manager = login("manager@company.local");
        String email = "assigned." + UUID.randomUUID() + "@company.local";
        var createUser = mockMvc.perform(post("/api/admin/users")
                        .with(csrf()).cookie(admin).contentType("application/json")
                        .content(createUserJson(email, "WORKER")))
                .andExpect(status().isCreated()).andReturn();
        JsonNode userBody = objectMapper.readTree(createUser.getResponse().getContentAsString());
        String userId = userBody.get("id").asText();
        String invitationToken = invitationToken(email);
        mockMvc.perform(post("/api/auth/invitations/accept").with(csrf()).contentType("application/json")
                        .content("""
                                {"token":"%s","password":"Assigned worker passphrase 2026"}
                                """.formatted(invitationToken)))
                .andExpect(status().isOk());
        long version = userRepository.findById(UUID.fromString(userId)).orElseThrow().getVersion();

        mockMvc.perform(post("/api/tasks").with(csrf()).cookie(manager).contentType("application/json")
                        .content("""
                                {"title":"Open assignment","description":null,"assigneeId":"%s","dueDate":null}
                                """.formatted(userId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/users/{id}/deactivate", userId)
                        .with(csrf()).cookie(admin).contentType("application/json")
                        .content("{\"version\":" + version + "}"))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/admin/users/{id}", userId)
                        .with(csrf()).cookie(admin).contentType("application/json")
                        .content("""
                                {
                                  "version": %d,
                                  "firstName": "Assigned",
                                  "lastName": "Worker",
                                  "email": "%s",
                                  "jobTitle": null,
                                  "department": null,
                                  "phoneNumber": null,
                                  "role": "MANAGER"
                                }
                                """.formatted(version, email)))
                .andExpect(status().isConflict());
    }

    private Cookie establishedAdminCookie() throws Exception {
        Cookie temporaryCookie = loginWithPassword("admin@company.local", BOOTSTRAP_PASSWORD, true);
        return mockMvc.perform(post("/api/auth/change-password")
                        .with(csrf()).cookie(temporaryCookie).contentType("application/json")
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}
                                """.formatted(BOOTSTRAP_PASSWORD, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("TASK_ACCESS");
    }

    private Cookie loginWithPassword(String email, String password, boolean mustChangePassword) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(mustChangePassword))
                .andReturn().getResponse().getCookie("TASK_ACCESS");
    }

    private String createUserJson(String email, String role) {
        return """
                {
                  "firstName": "New",
                  "lastName": "User",
                  "email": "%s",
                  "jobTitle": "Analyst",
                  "department": "Operations",
                  "phoneNumber": "+14155552671",
                  "role": "%s"
                }
                """.formatted(email, role);
    }

    private String invitationToken(String email) {
        String body = emailOutboxRepository.findTopByRecipientOrderByCreatedAtDesc(email)
                .orElseThrow().getTextBody();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("token=([A-Za-z0-9_-]+)").matcher(body);
        assertTrue(matcher.find());
        return matcher.group(1);
    }
}
