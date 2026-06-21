package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.EmailVerificationToken;
import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.repository.EmailVerificationTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.verification.code-expiration-minutes:10}")
    private long codeExpirationMinutes;

    @Value("${app.verification.code-length:6}")
    private int codeLength;

    public VerificationService(EmailVerificationTokenRepository tokenRepository,
                               PasswordEncoder passwordEncoder,
                               MailService mailService) {
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
    }

    public void sendCode(User user, VerificationPurpose purpose) {
        String code = generateCode(codeLength);
        persistToken(user, purpose, code);
        try {
            mailService.sendPlainText(
                    user.getEmail(),
                    subjectFor(purpose),
                    bodyFor(purpose, code)
            );
        } catch (Exception e) {
            log.error("Failed to send verification email to {} for {}: {}",
                maskEmail(user.getEmail()), purpose, e.getMessage());
            throw new IllegalStateException("Unable to send verification code. Please try again later.", e);
        }
    }

    public void verifyOrThrow(User user, String code, VerificationPurpose purpose) {
        EmailVerificationToken token = tokenRepository
                .findTopByUserUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(
                        user.getUserId(), purpose.name())
                .orElseThrow(() -> new IllegalArgumentException("Verification code does not exist or has already been used."));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Verification code has expired. Please request a new code.");
        }
        if (code == null || !passwordEncoder.matches(code, token.getCodeHash())) {
            throw new IllegalArgumentException("Incorrect verification code.");
        }
        token.setUsed(true);
        tokenRepository.save(token);
    }

    public void checkCodeOnlyOrThrow(User user, String code, VerificationPurpose purpose) {
        EmailVerificationToken token = tokenRepository
                .findTopByUserUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(
                        user.getUserId(), purpose.name())
                .orElseThrow(() -> new IllegalArgumentException("Verification code does not exist or has already been used."));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Verification code has expired. Please request a new code.");
        }
        if (code == null || !passwordEncoder.matches(code, token.getCodeHash())) {
            throw new IllegalArgumentException("Incorrect verification code.");
        }
    }

    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "******";
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String domain = parts[1];

        if (name.length() <= 3) {
            return name.charAt(0) + "..@" + domain;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(name.charAt(0));
        int dots = Math.max(2, name.length() - 3);
        for (int i = 0; i < dots; i++) {
            sb.append('.');
        }
        sb.append(name.substring(name.length() - 2));
        return sb + "@" + domain;
    }

    private void persistToken(User user, VerificationPurpose purpose, String code) {
        tokenRepository.deleteByUserUserIdAndPurpose(user.getUserId(), purpose.name());

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setPurpose(purpose.name());
        token.setCodeHash(passwordEncoder.encode(code));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(codeExpirationMinutes));
        token.setUsed(false);
        tokenRepository.save(token);
    }

    private String generateCode(int length) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < length; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    private String subjectFor(VerificationPurpose purpose) {
        return switch (purpose) {
            case EMAIL_VERIFY -> "Verify MovieRec Email";
            case PASSWORD_CHANGE -> "Password Change Verification Code";
            case PASSWORD_RESET -> "Password Reset Code";
        };
    }

    private String bodyFor(VerificationPurpose purpose, String code) {
        String purposeText = switch (purpose) {
            case EMAIL_VERIFY -> "verify your email address";
            case PASSWORD_CHANGE -> "confirm your password change request";
            case PASSWORD_RESET -> "reset your password";
        };
        return """
Hello,

Your verification code is: %s

This code is valid for %d minutes. If you did not make a request to %s, please ignore this email.

Thank you for using MovieRec!
""".formatted(code, codeExpirationMinutes, purposeText);
    }
}
