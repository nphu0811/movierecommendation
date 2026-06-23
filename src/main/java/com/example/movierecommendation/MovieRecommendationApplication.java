package com.example.movierecommendation;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MovieRecommendationApplication {
    public static void main(String[] args) {
        SpringApplication.run(MovieRecommendationApplication.class, args);
    }

    @Bean
    public CommandLineRunner dropFlywayTable(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("DROP TABLE IF EXISTS flyway_schema_history CASCADE;");
                System.out.println("Successfully dropped flyway_schema_history table if it existed.");
            } catch (Exception e) {
                System.err.println("Error dropping flyway_schema_history: " + e.getMessage());
            }
        };
    }
}
