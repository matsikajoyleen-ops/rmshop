package com.rmshop.rmshop.repository;

import com.rmshop.rmshop.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByAccessCodeAndActiveTrue(String accessCode);
    boolean existsByAccessCode(String accessCode);
}