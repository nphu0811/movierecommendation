package com.example.movierecommendation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
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

        HttpClient httpClient = HttpClient.create()
                .resolver(spec -> spec
                        .dnsServer("8.8.8.8")
                        .dnsServer("1.1.1.1")
                        .ndots(1)
                        .dnsQueryTimeout(Duration.ofSeconds(5)))
                .responseTimeout(Duration.ofSeconds(15));

        this.brevoClient = WebClient.builder()
                .baseUrl("https://api.brevo.com/v3")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("api-key", apiKey)
                .build();
    }

    public void sendPlainText(String to, String subject, String content) {
        sendPlainText(List.of(to), subject, content);
    }

    /**
     * Send the same content to multiple recipients using Brevo HTTP API.
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
            log.error("Failed to send email via Brevo HTTP. Status {} Body {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new RuntimeException("Gửi email thất bại: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to send email via Brevo HTTP", ex);
            throw new RuntimeException("Gửi email thất bại: " + ex.getMessage());
        }
    }
}
