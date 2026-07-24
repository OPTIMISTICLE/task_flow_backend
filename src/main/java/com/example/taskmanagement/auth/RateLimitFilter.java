package com.example.taskmanagement.auth;

import com.example.taskmanagement.audit.AuditService;
import com.example.taskmanagement.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> apiBuckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper, AuditService auditService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || "/api/auth/csrf".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean login = "/api/auth/login".equals(request.getRequestURI())
                || "/api/auth/mfa/challenge".equals(request.getRequestURI());
        String identity = login ? request.getRemoteAddr() : authenticatedIdentity(request);
        Map<String, Bucket> buckets = login ? loginBuckets : apiBuckets;
        Bucket bucket = buckets.computeIfAbsent(identity, ignored -> newBucket(login));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining", Long.toString(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfter));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser actor = authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                ? user : null;
        auditService.denied(actor, "RATE_LIMIT_EXCEEDED", request.getMethod() + " " + request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "title", "Too many requests",
                "status", 429,
                "detail", "The request rate limit has been exceeded.",
                "instance", request.getRequestURI()
        ));
    }

    private Bucket newBucket(boolean login) {
        long capacity = login ? properties.loginCapacity() : properties.apiCapacity();
        long refill = login ? properties.loginRefill() : properties.apiRefill();
        java.time.Duration period = login ? properties.loginPeriod() : properties.apiPeriod();
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(refill, period))
                .build();
    }

    private String authenticatedIdentity(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return "user:" + user.id();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
