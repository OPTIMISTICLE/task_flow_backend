package com.example.taskmanagement.user;

import com.example.taskmanagement.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn();
        JsonNode created = objectMapper.readTree(createdResponse.getResponse().getContentAsString());
        assertTrue(created.get("temporaryPassword").asText().length() >= 15);
        String userId = created.at("/user/id").asText();
        long version = created.at("/user/version").asLong();

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
    void temporaryPasswordRestrictsAccessAndChangingItInvalidatesTheOldCookie() throws Exception {
        Cookie admin = establishedAdminCookie();
        String email = "temporary." + UUID.randomUUID() + "@company.local";
        var response = mockMvc.perform(post("/api/admin/users")
                        .with(csrf()).cookie(admin).contentType("application/json")
                        .content(createUserJson(email, "WORKER")))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(response.getResponse().getContentAsString());
        String temporaryPassword = body.get("temporaryPassword").asText();
        Cookie temporaryCookie = loginWithPassword(email, temporaryPassword, true);

        mockMvc.perform(get("/api/tasks").cookie(temporaryCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        mockMvc.perform(post("/api/auth/change-password")
                        .with(csrf()).cookie(temporaryCookie).contentType("application/json")
                        .content("""
                                {"currentPassword":"%s","newPassword":"A new private passphrase 2026"}
                                """.formatted(temporaryPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        mockMvc.perform(get("/api/auth/me").cookie(temporaryCookie))
                .andExpect(status().isUnauthorized());
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
        JsonNode userBody = objectMapper.readTree(createUser.getResponse().getContentAsString()).get("user");
        String userId = userBody.get("id").asText();
        long version = userBody.get("version").asLong();

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
}
