package com.example.taskmanagement.auth;

import com.example.taskmanagement.audit.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/auth/csrf", "/api/auth/me", "/api/auth/logout", "/api/auth/change-password",
            "/api/auth/sessions"
    );

    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public PasswordChangeRequiredFilter(ObjectMapper objectMapper, AuditService auditService) {
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                && user.mustChangePassword() && request.getRequestURI().startsWith("/api/")
                && !ALLOWED_PATHS.contains(request.getRequestURI()) && !"OPTIONS".equals(request.getMethod())) {
            auditService.denied(user, "PASSWORD_CHANGE_REQUIRED",
                    request.getMethod() + " " + request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("Cache-Control", "no-store");
            objectMapper.writeValue(response.getOutputStream(), Map.of(
                    "title", "Password change required",
                    "status", 403,
                    "detail", "Change the temporary password before continuing.",
                    "code", "PASSWORD_CHANGE_REQUIRED",
                    "instance", request.getRequestURI()
            ));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
