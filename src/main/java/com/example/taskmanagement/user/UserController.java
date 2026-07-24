package com.example.taskmanagement.user;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository repository;

    public UserController(UserRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public List<UserSummaryResponse> listByRole(@RequestParam(defaultValue = "WORKER") UserRole role) {
        if (role != UserRole.WORKER) {
            return List.of();
        }
        return repository.findByRoleAndAccountStatusOrderByFirstNameAscLastNameAsc(
                        role, UserAccountStatus.ACTIVE).stream()
                .map(UserSummaryResponse::from)
                .toList();
    }
}
