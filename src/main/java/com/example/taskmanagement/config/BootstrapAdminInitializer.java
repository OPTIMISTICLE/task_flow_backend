package com.example.taskmanagement.config;

import com.example.taskmanagement.auth.PasswordPolicy;
import com.example.taskmanagement.user.User;
import com.example.taskmanagement.user.UserAccountStatus;
import com.example.taskmanagement.user.UserRepository;
import com.example.taskmanagement.user.UserRole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class BootstrapAdminInitializer implements ApplicationRunner {
    private final BootstrapAdminProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final Clock clock;

    public BootstrapAdminInitializer(BootstrapAdminProperties properties, UserRepository users,
                                     PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy, Clock clock) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRoleAndAccountStatus(UserRole.ADMIN, UserAccountStatus.ACTIVE)) {
            return;
        }
        if (!properties.complete()) {
            throw new IllegalStateException("An active administrator or complete BOOTSTRAP_ADMIN_* configuration is required");
        }
        passwordPolicy.validate(properties.password());
        if (users.existsByEmailIgnoreCase(properties.email())) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL is already used by a non-administrator account");
        }
        User admin = new User(properties.firstName().strip(), properties.lastName().strip(),
                properties.email(), passwordEncoder.encode(properties.password()), UserRole.ADMIN,
                UserAccountStatus.ACTIVE, null, null, null, true, clock.instant());
        users.save(admin);
    }
}
