package com.example.taskmanagement.config;

import com.example.taskmanagement.audit.AuditService;
import com.example.taskmanagement.user.User;
import com.example.taskmanagement.user.UserRepository;
import com.example.taskmanagement.user.UserRole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final BootstrapAdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final AuditService auditService;

    public BootstrapAdminInitializer(BootstrapAdminProperties properties, UserRepository userRepository,
                                     PasswordEncoder passwordEncoder, Clock clock, AuditService auditService) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRoleAndActiveTrue(UserRole.ADMIN)) {
            return;
        }
        require(properties.email(), "BOOTSTRAP_ADMIN_EMAIL");
        require(properties.firstName(), "BOOTSTRAP_ADMIN_FIRST_NAME");
        require(properties.lastName(), "BOOTSTRAP_ADMIN_LAST_NAME");
        requirePassword(properties.password());
        if (userRepository.existsByEmailIgnoreCase(properties.email())) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL is already used by a non-administrator account");
        }
        User admin = new User(properties.firstName().strip(), properties.lastName().strip(),
                properties.email().strip(), passwordEncoder.encode(properties.password()), UserRole.ADMIN,
                null, null, null, true, clock.instant());
        userRepository.saveAndFlush(admin);
        auditService.success(null, "BOOTSTRAP_ADMIN_CREATED", "USER", admin.getId(),
                "Initial administrator created");
    }

    private void require(String value, String variable) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(variable + " is required when no active administrator exists");
        }
    }

    private void requirePassword(String password) {
        if (password == null || password.length() < 15 || password.length() > 200) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must contain between 15 and 200 characters");
        }
    }
}
