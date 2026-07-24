package com.example.taskmanagement.config;

import com.example.taskmanagement.user.User;
import com.example.taskmanagement.user.UserRepository;
import com.example.taskmanagement.user.UserRole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SeedDataInitializer implements ApplicationRunner {

    private final SeedProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedDataInitializer(SeedProperties properties, UserRepository userRepository,
                               PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled() || userRepository.count() > 0) {
            return;
        }
        if (properties.password() == null || properties.password().length() < 10) {
            throw new IllegalStateException("SEED_PASSWORD must contain at least 10 characters when seed users are enabled");
        }
        String passwordHash = passwordEncoder.encode(properties.password());
        userRepository.save(new User("Maya", "Manager", "manager@company.local", passwordHash, UserRole.MANAGER));
        userRepository.save(new User("William", "Worker", "worker1@company.local", passwordHash, UserRole.WORKER));
        userRepository.save(new User("Wendy", "Worker", "worker2@company.local", passwordHash, UserRole.WORKER));
    }
}
