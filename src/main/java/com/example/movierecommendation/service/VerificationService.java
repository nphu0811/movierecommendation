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
        String purposeTextEn = switch (purpose) {
            case EMAIL_VERIFY -> "verify your email address";
            case PASSWORD_CHANGE -> "confirm your password change request";
            case PASSWORD_RESET -> "reset your password";
        };
        String purposeTextVi = switch (purpose) {
            case EMAIL_VERIFY -> "xác thực địa chỉ email";
            case PASSWORD_CHANGE -> "xác nhận yêu cầu thay đổi mật khẩu";
            case PASSWORD_RESET -> "đặt lại mật khẩu";
        };
        return """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
    body {
      margin: 0;
      padding: 0;
      background-color: #141414;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      color: #ffffff;
      -webkit-font-smoothing: antialiased;
    }
    .wrapper {
      width: 100%%;
      table-layout: fixed;
      background-color: #141414;
      padding: 40px 0;
    }
    .container {
      max-width: 500px;
      margin: 0 auto;
      background-color: #1f1f1f;
      border-radius: 16px;
      border: 1px solid #2d2d2d;
      overflow: hidden;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
    }
    .header {
      padding: 30px;
      text-align: center;
      border-bottom: 1px solid #2d2d2d;
      background: linear-gradient(135deg, #1f1f1f 0%%, #151515 100%%);
    }
    .logo {
      font-size: 28px;
      font-weight: 800;
      color: #ffffff;
      text-decoration: none;
      letter-spacing: -0.5px;
    }
    .logo span {
      color: #E50914;
    }
    .content {
      padding: 40px 30px;
      text-align: center;
    }
    .code-container {
      margin: 30px 0;
      padding: 20px;
      background-color: #141414;
      border-radius: 12px;
      border: 1px dashed #E50914;
      display: inline-block;
    }
    .code {
      font-size: 36px;
      font-weight: 800;
      color: #E50914;
      letter-spacing: 6px;
      margin: 0;
      padding-left: 6px;
    }
    .footer {
      padding: 20px 30px;
      background-color: #151515;
      text-align: center;
      border-top: 1px solid #2d2d2d;
    }
  </style>
</head>
<body>
  <div class="wrapper">
    <div class="container">
      <div class="header">
        <div class="logo">Movie<span>Rec</span></div>
      </div>
      <div class="content">
        <h2 style="margin-top: 0; color: #ffffff; font-size: 20px; font-weight: 700; margin-bottom: 20px;">Verification Code / Mã xác thực</h2>
        <p style="color: #b3b3b3; font-size: 14px; line-height: 1.6; margin: 10px 0; text-align: left;">Hello / Xin chào,</p>
        <p style="color: #b3b3b3; font-size: 14px; line-height: 1.6; margin: 10px 0; text-align: left;">You requested a verification code to <strong>%s</strong>.<br>Bạn đã yêu cầu mã xác thực để <strong>%s</strong>.</p>
        <div class="code-container">
          <div class="code">%s</div>
        </div>
        <p style="font-size: 12px; color: #8c8c8c; line-height: 1.6; margin-top: 20px; text-align: left;">This code is valid for <strong>%d minutes</strong>. If you did not make this request, please ignore this email.<br>Mã này có hiệu lực trong vòng <strong>%d phút</strong>. Nếu bạn không gửi yêu cầu này, vui lòng bỏ qua email.</p>
      </div>
      <div class="footer">
        <p style="font-size: 12px; color: #777777; margin: 0;">Thank you for using MovieRec! / Cảm ơn bạn đã lựa chọn MovieRec!</p>
      </div>
    </div>
  </div>
</body>
</html>
""".formatted(purposeTextEn, purposeTextVi, code, codeExpirationMinutes, codeExpirationMinutes);
    }
}
