package com.example.movierecommendation.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final Resend resend;
    private final String fromAddress;

    public MailService(
            @Value("${resend.api.key:}") String apiKey,
            @Value("${app.mail.from:noreply@movierec.local}") String fromAddress) {
        this.resend = new Resend(apiKey);
        this.fromAddress = fromAddress;
    }

    public void sendPlainText(String to, String subject, String content) {
        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromAddress)
                .to(to)
                .subject(subject)
                .text(content)
                .build();

        try {
            resend.emails().send(params);
            log.info("Verification email sent to {}", to);
        } catch (ResendException ex) {
            log.error("Failed to send email to {}", to, ex);
            throw new RuntimeException("Gửi email thất bại: " + ex.getMessage());
        }
    }
}
