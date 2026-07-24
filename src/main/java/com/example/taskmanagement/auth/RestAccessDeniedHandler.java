package com.example.taskmanagement.auth;

import com.example.taskmanagement.audit.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public RestAccessDeniedHandler(ObjectMapper objectMapper, AuditService auditService) {
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser actor = authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                ? user : null;
        boolean csrfRejected = accessDeniedException instanceof CsrfException;
        auditService.denied(actor, csrfRejected ? "CSRF_REJECTED" : "ACCESS_DENIED",
                request.getMethod() + " " + request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "title", csrfRejected ? "CSRF validation failed" : "Access denied",
                "status", 403,
                "detail", csrfRejected
                        ? "The CSRF token is missing or expired. Refresh it and try again."
                        : "You are not allowed to perform this operation.",
                "instance", request.getRequestURI()
        ));
    }
}
