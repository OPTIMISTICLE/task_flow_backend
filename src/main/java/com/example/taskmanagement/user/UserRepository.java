package com.example.taskmanagement.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    boolean existsByRoleAndActiveTrue(UserRole role);

    long countByRoleAndActiveTrue(UserRole role);

    List<User> findByRoleAndActiveTrueOrderByFirstNameAscLastNameAsc(UserRole role);
}
