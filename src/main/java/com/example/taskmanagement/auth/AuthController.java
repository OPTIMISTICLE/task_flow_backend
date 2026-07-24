package com.example.taskmanagement.auth;

import com.example.taskmanagement.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtAuthenticationFilter jwtFilter;
    private final TokenRevocationService revocationService;
    private final AuditService auditService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
                          JwtAuthenticationFilter jwtFilter, TokenRevocationService revocationService,
                          AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtFilter = jwtFilter;
        this.revocationService = revocationService;
        this.auditService = auditService;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return new CsrfResponse(csrfToken.getHeaderName());
    }

    @PostMapping("/login")
    public AuthUserResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
            AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
            String token = jwtService.issue(user);
            response.addHeader(HttpHeaders.SET_COOKIE, accessCookie(token, jwtService.properties().ttl()).toString());
            auditService.success(user, "AUTH_LOGIN", "USER", user.id(), "Authentication succeeded");
            return AuthUserResponse.from(user);
        } catch (AuthenticationException exception) {
            auditService.failure(request.email(), "AUTH_LOGIN", "USER", null, "Authentication rejected");
            throw exception;
        }
    }

    @GetMapping("/me")
    public AuthUserResponse me(Authentication authentication) {
        return AuthUserResponse.from((AuthenticatedUser) authentication.getPrincipal());
    }

    @PostMapping("/logout")
    public void logout(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        jwtFilter.findToken(request).ifPresent(token -> {
            try {
                Jwt jwt = jwtService.decode(token);
                revocationService.revoke(jwt);
            } catch (RuntimeException ignored) {
                // An invalid token is cleared just like a valid token.
            }
        });
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie("", Duration.ZERO).toString());
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            auditService.success(user, "AUTH_LOGOUT", "USER", user.id(), "Session cookie cleared");
        }
    }

    private ResponseCookie accessCookie(String value, Duration maxAge) {
        return ResponseCookie.from(jwtService.properties().cookieName(), value)
                .httpOnly(true)
                .secure(jwtService.properties().cookieSecure())
                .sameSite(jwtService.properties().cookieSameSite())
                .path("/api")
                .maxAge(maxAge)
                .build();
    }
}
