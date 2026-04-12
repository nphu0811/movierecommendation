package com.example.movierecommendation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final WebClient brevoClient;
    private final String fromAddress;

    public MailService(
            @Value("${brevo.api.key}") String apiKey,
            @Value("${app.mail.from:noreply@movierec.local}") String fromAddress) {
        this.fromAddress = fromAddress;
        this.brevoClient = WebClient.builder()
                .baseUrl("https://api.brevo.com/v3")
                .defaultHeader("api-key", apiKey)
                .build();
    }

    public void sendPlainText(String to, String subject, String content) {
        sendPlainText(List.of(to), subject, content);
    }

    /**
     * Send the same content to multiple recipients using Brevo transactional email HTTP API.
     * Using HTTP avoids SMTP port blocks on hosting providers.
     */
    public void sendPlainText(Collection<String> recipients, String subject, String content) {
        if (recipients == null || recipients.isEmpty()) {
            log.warn("No recipients provided, skip sending email");
            return;
        }
        try {
            Map<String, Object> body = Map.of(
                    "sender", Map.of("email", fromAddress),
                    "to", recipients.stream().map(e -> Map.of("email", e)).toList(),
                    "subject", subject,
                    "htmlContent", content
            );
            brevoClient.post()
                    .uri("/smtp/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            log.info("Verification email sent to {}", recipients);
        } catch (WebClientResponseException ex) {
            log.error("Failed to send email via Brevo. Status {} Body {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new RuntimeException("Gửi email thất bại: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to send email via Brevo", ex);
            throw new RuntimeException("Gửi email thất bại: " + ex.getMessage());
        }
    }
}
