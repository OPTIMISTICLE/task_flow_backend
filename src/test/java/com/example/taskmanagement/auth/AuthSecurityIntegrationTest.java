package com.example.taskmanagement.auth;

import com.example.taskmanagement.IntegrationTestSupport;
import com.example.taskmanagement.audit.AuditEventRepository;
import com.example.taskmanagement.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthSecurityIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Test
    void loginUsesBcryptAndIssuesAnHttpOnlyJwtCookie() throws Exception {
        String passwordHash = userRepository.findByEmailIgnoreCase("manager@company.local").orElseThrow().getPasswordHash();
        assertThat(passwordHash).startsWith("$2").doesNotContain(PASSWORD);

        var response = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"email":"manager@company.local","password":"TestPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("TASK_ACCESS", true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("SameSite=Strict")))
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("eyJ"))))
                .andReturn().getResponse();

        assertThat(response.getCookie("TASK_ACCESS")).isNotNull();
    }

    @Test
    void invalidCredentialsAreRejectedWithoutLeakingAccountState() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"email":"unknown@company.local","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void csrfAndAuthenticationAreBothRequired() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"manager@company.local","password":"TestPassword123!"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("CSRF validation failed"))
                .andExpect(jsonPath("$.detail").value(
                        "The CSRF token is missing or expired. Refresh it and try again."));

        assertThat(auditEventRepository.existsByAction("CSRF_REJECTED")).isTrue();

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }

    @Test
    void logoutRevokesThePresentedJwt() throws Exception {
        Cookie jwt = login("manager@company.local");

        mockMvc.perform(get("/api/auth/me").cookie(jwt))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").cookie(jwt).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("TASK_ACCESS", 0));

        mockMvc.perform(get("/api/auth/me").cookie(jwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsOnlyTheConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/tasks")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mockMvc.perform(options("/api/tasks")
                        .header(HttpHeaders.ORIGIN, "https://attacker.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void authenticatedResponsesIncludeTheExpectedSecurityHeaders() throws Exception {
        Cookie jwt = login("manager@company.local");
        mockMvc.perform(get("/api/tasks").cookie(jwt))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
    }
}
