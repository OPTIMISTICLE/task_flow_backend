package com.example.taskmanagement.user;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminUserPageResponse(
        List<AdminUserResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static AdminUserPageResponse from(Page<User> users) {
        return new AdminUserPageResponse(users.getContent().stream().map(AdminUserResponse::from).toList(),
                users.getNumber(), users.getSize(), users.getTotalElements(), users.getTotalPages());
    }
}
