package com.example.movierecommendation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public MailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from:noreply@movierec.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendPlainText(String to, String subject, String content) {
        sendPlainText(List.of(to), subject, content);
    }

    /**
     * Send the same plain-text content to multiple recipients via Brevo SMTP.
     */
    public void sendPlainText(Collection<String> recipients, String subject, String content) {
        if (recipients == null || recipients.isEmpty()) {
            log.warn("No recipients provided, skip sending email");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(recipients.toArray(new String[0]));
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            log.info("Verification email sent to {}", recipients);
        } catch (Exception ex) {
            log.error("Failed to send email via Brevo SMTP", ex);
            throw new RuntimeException("Gửi email thất bại: " + ex.getMessage());
        }
    }
}
