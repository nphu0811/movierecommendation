package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findTopByUserUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(
            Integer userId, String purpose);

    void deleteByUserUserIdAndPurpose(Integer userId, String purpose);

    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
