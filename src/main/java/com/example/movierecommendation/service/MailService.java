package com.example.movierecommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String fromAddress;
    private final String apiKey;

    public MailService(
            @Value("${brevo.api.key}") String apiKey,
            @Value("${app.mail.from:noreply@movierec.local}") String fromAddress) {
        this.fromAddress = fromAddress;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void sendPlainText(String to, String subject, String content) {
        sendPlainText(List.of(to), subject, content);
    }

    /**
     * Send the same content to multiple recipients using Brevo HTTP API.
     * Uses JDK HttpClient (system resolver) to avoid Netty DNS issues on the host.
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

            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Verification email sent to {}", recipients);
            } else {
                log.error("Failed to send email via Brevo HTTP. Status {} Body {}", response.statusCode(), response.body());
                throw new RuntimeException("Gửi email thất bại: HTTP " + response.statusCode());
            }
        } catch (Exception ex) {
            log.error("Failed to send email via Brevo HTTP", ex);
            throw new RuntimeException("Gửi email thất bại: " + ex.getMessage());
        }
    }
}
