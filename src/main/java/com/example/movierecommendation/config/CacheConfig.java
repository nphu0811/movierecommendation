package com.example.movierecommendation.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration for the recommendation engine.
 *
 * <p>Uses Caffeine as the backing store to support TTL-based expiry.
 * The {@code recommendations} cache holds per-user recommendation lists
 * and expires entries after 1 hour of write, keeping results fresh
 * without recalculating on every request.
 *
 * <p>{@link EnableCaching} is declared here (and also on {@link AppConfig})
 * so this class is self-contained and the intent is explicit.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("recommendations");
        manager.setCaffeine(
            Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(1_000)
        );
        return manager;
    }
}
