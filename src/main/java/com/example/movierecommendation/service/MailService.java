package com.example.movierecommendation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Collection;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public MailService(JavaMailSender mailSender,
                       @Value("${app.mail.from:noreply@movierec.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendPlainText(String to, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);
            mailSender.send(message);
            log.info("Verification email sent to {}", to);
        } catch (MessagingException ex) {
            log.error("Failed to send email to {}", to, ex);
            throw new RuntimeException("Gửi email thất bại: " + ex.getMessage());
        }
    }

    /**
     * Send the same content to multiple recipients.
     * We loop through individual sends to keep behaviour consistent with the single-recipient API.
     */
    public void sendPlainText(Collection<String> recipients, String subject, String content) {
        if (recipients == null || recipients.isEmpty()) {
            log.warn("No recipients provided, skip sending email");
            return;
        }
        recipients.forEach(to -> sendPlainText(to, subject, content));
    }
}
