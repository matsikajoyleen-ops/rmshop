package com.rmshop.rmshop.repository;

import com.rmshop.rmshop.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(Long userId);
    List<PasswordResetToken> findByUserIdAndUsedAtIsNull(Long userId);
}
