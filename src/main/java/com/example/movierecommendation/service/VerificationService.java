package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.EmailVerificationToken;
import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.repository.EmailVerificationTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class VerificationService {

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
        mailService.sendPlainText(
                user.getEmail(),
                subjectFor(purpose),
                bodyFor(purpose, code)
        );
    }

    public void verifyOrThrow(User user, String code, VerificationPurpose purpose) {
        EmailVerificationToken token = tokenRepository
                .findTopByUserUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(
                        user.getUserId(), purpose.name())
                .orElseThrow(() -> new IllegalArgumentException("Mã xác thực không tồn tại hoặc đã dùng."));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Mã xác thực đã hết hạn. Hãy gửi lại mã.");
        }
        if (!passwordEncoder.matches(code, token.getCodeHash())) {
            throw new IllegalArgumentException("Mã xác thực không đúng.");
        }
        token.setUsed(true);
        tokenRepository.save(token);
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
            case EMAIL_VERIFY -> "Xác thực email MovieRec";
            case PASSWORD_CHANGE -> "Mã xác thực đổi mật khẩu";
            case PASSWORD_RESET -> "Mã khôi phục mật khẩu";
        };
    }

    private String bodyFor(VerificationPurpose purpose, String code) {
        String purposeText = switch (purpose) {
            case EMAIL_VERIFY -> "xác thực địa chỉ email của bạn";
            case PASSWORD_CHANGE -> "xác nhận yêu cầu đổi mật khẩu";
            case PASSWORD_RESET -> "khôi phục mật khẩu của bạn";
        };
        return """
Xin chào,

Mã xác thực của bạn là: %s

Mã có hiệu lực trong %d phút. Nếu bạn không thực hiện yêu cầu %s, vui lòng bỏ qua email này.

Cảm ơn bạn đã sử dụng MovieRec!
""".formatted(code, codeExpirationMinutes, purposeText);
    }
}
