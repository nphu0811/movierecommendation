package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.EmailVerificationToken;
import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.repository.EmailVerificationTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationServiceSecurityTest {

    @Mock EmailVerificationTokenRepository tokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock MailService mailService;

    @Test
    void fixedDemoCodeCannotBypassOtpVerification() {
        User user = new User();
        user.setUserId(7);
        EmailVerificationToken token = new EmailVerificationToken();
        token.setCodeHash("real-hash");
        token.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(tokenRepository.findTopByUserUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(
            7, VerificationPurpose.PASSWORD_RESET.name())).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("123456", "real-hash")).thenReturn(false);

        VerificationService service = new VerificationService(tokenRepository, passwordEncoder, mailService);

        assertThrows(IllegalArgumentException.class, () ->
            service.verifyOrThrow(user, "123456", VerificationPurpose.PASSWORD_RESET));
        verify(tokenRepository, never()).save(token);
    }
}
