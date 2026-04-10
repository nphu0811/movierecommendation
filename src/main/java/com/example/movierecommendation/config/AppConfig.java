package com.example.movierecommendation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

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

    /**
     * Shared thread pool for homepage concurrent fetches (recommendations, genre picks).
     * Avoids tạo ExecutorService mới mỗi request và giữ số thread ở mức an toàn.
     */
    @Bean(name = "homePageExecutor")
    public Executor homePageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("home-");
        executor.initialize();
        return executor;
    }
}
