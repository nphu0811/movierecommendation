package com.example.movierecommendation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
@EnableCaching
@EnableScheduling
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // WebClient cho OpenAI calls - non-blocking, reuse connection pool
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
            .exchangeStrategies(ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)) // 2MB buffer
                .build());
    }
}