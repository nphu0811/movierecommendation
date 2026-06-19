package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.*;
import com.example.movierecommendation.repository.RatingRepository;
import com.example.movierecommendation.repository.WatchHistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OpenAIService {

    private static final Logger log = LoggerFactory.getLogger(OpenAIService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    // In-memory cache: key = prompt hash, value = [result, timestamp]
    private final Map<String, Object[]> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(20);

    @Value("${openai.api.key:}")
    private String apiKey;

    @Autowired private RatingRepository ratingRepository;
    @Autowired private WatchHistoryRepository watchHistoryRepository;

    // WebClient singleton - do not recreate per request
    private WebClient webClient;

    private WebClient getWebClient() {
        if (webClient == null) {
            String cleanKey = apiKey != null ? apiKey.replace("\"", "").trim() : "";
            webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cleanKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        }
        return webClient;
    }

    public boolean isEnabled() {
        String cleanKey = apiKey != null ? apiKey.replace("\"", "").trim() : "";
        boolean enabled = !cleanKey.isEmpty();
        if (enabled && log.isDebugEnabled()) {
             log.debug("OpenAI API is enabled. Key: {}***", cleanKey.substring(0, Math.min(4, cleanKey.length())));
        }
        return enabled;
    }

    public String generateMovieSummary(String title, String description) {
        if (!isEnabled()) return null;
        try {
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            String prompt = isVi 
                ? String.format("Hãy tóm tắt phim '%s' dựa trên mô tả: '%s'. Tóm tắt bằng tiếng Việt, khoảng 2-3 câu ngắn gọn, lôi cuốn, không có lời mở đầu hay kết luận.", title, description)
                : String.format("Summarize the movie '%s' based on the description: '%s'. Summarize in English, in about 2-3 concise, engaging sentences, without introduction or conclusion.", title, description);
            
            Map<String, Object> body = new HashMap<>();
            body.put("model", "gpt-4o-mini");
            body.put("max_tokens", 300);
            body.put("temperature", 0.7);
            body.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
            ));

            String response = getWebClient().post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(6))
                .block();

            if (response == null) return null;

            JsonNode root = mapper.readTree(response);
            String content = root.at("/choices/0/message/content").asText("").trim();
            return content.isEmpty() ? null : content;
        } catch (Exception e) {
            log.warn("OpenAI generateMovieSummary failed for '{}': {}", title, e.getMessage());
            return null;
        }
    }

    public List<Integer> getAIRecommendedMovieIds(Integer userId, List<Movie> allMovies) {
        if (!isEnabled()) return Collections.emptyList();

        try {
            List<Rating> ratings = ratingRepository.findByUserUserId(userId);
            List<WatchHistory> history = watchHistoryRepository.findByUserUserIdOrderByWatchedAtAsc(userId);

            if (ratings.isEmpty() && history.isEmpty()) return Collections.emptyList();

            // Build SHORT prompt - only get what is necessary
            String prompt = buildCompactPrompt(ratings, history, allMovies);
            if (prompt == null) return Collections.emptyList();

            // Check cache before calling API
            String cacheKey = String.valueOf(prompt.hashCode());
            List<Integer> cached = getFromCache(cacheKey);
            if (cached != null) {
                log.debug("Cache hit for user {}", userId);
                return cached;
            }

            // Call OpenAI using WebClient (non-blocking with timeout)
            List<Integer> result = callOpenAIAsync(prompt);

            // Save to cache
            if (!result.isEmpty()) {
                putInCache(cacheKey, result);
            }
            return result;

        } catch (Exception e) {
            log.warn("OpenAI recommendation failed for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    // Concise prompt - reduces tokens by 60-70% compared to old one
    private String buildCompactPrompt(List<Rating> ratings, List<WatchHistory> history, List<Movie> allMovies) {
        List<String> loved = ratings.stream()
            .filter(r -> r.getRating() >= 4 && r.getMovie() != null)
            .sorted((a, b) -> Double.compare(b.getRating(), a.getRating()))
            .limit(5)
            .map(r -> r.getMovie().getTitle())
            .collect(Collectors.toList());

        List<String> disliked = ratings.stream()
            .filter(r -> r.getRating() <= 2 && r.getMovie() != null)
            .limit(3)
            .map(r -> r.getMovie().getTitle())
            .collect(Collectors.toList());

        if (loved.isEmpty() && history.isEmpty() && allMovies.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("User Preferences:\n");
        sb.append("- Liked Movies: ").append(loved.isEmpty() ? "None" : String.join(", ", loved)).append("\n");
        if (!disliked.isEmpty()) {
            sb.append("- Disliked Movies: ").append(String.join(", ", disliked)).append("\n");
        }
        sb.append("\nCandidates (Hybrid recommendations with scores and metadata):\n");
        for (Movie m : allMovies) {
            String genres = m.getGenres() != null ? m.getGenres().stream().map(Genre::getGenreName).collect(Collectors.joining(", ")) : "N/A";
            String desc = m.getDescription();
            if (desc != null && desc.length() > 150) {
                desc = desc.substring(0, 147) + "...";
            } else if (desc == null) {
                desc = "No description available.";
            }
            sb.append(String.format(
                "- MovieID: %d | Title: %s | Year: %s | Genres: [%s] | Rating: %.1f | Hybrid Score: %.3f | Desc: %s\n",
                m.getMovieId(), m.getTitle(), m.getReleaseYear() != null ? String.valueOf(m.getReleaseYear()) : "N/A",
                genres, m.getAverageRating(), m.getHybridScore(), desc
            ));
        }
        sb.append("\nTask:\n");
        sb.append("Based on the user's liked and disliked movies, rerank and select the top 5 most suitable movies from the candidates list above.\n");
        sb.append("Return the response matching the specified JSON Schema containing only the selected movieIds.");

        return sb.toString();
    }

    private List<Integer> callOpenAIAsync(String prompt) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", "gpt-4o-mini");
            body.put("max_tokens", 150);
            body.put("temperature", 0.3);
            body.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
            ));

            // Enforce Structured Outputs
            Map<String, Object> responseFormat = new HashMap<>();
            responseFormat.put("type", "json_schema");
            
            Map<String, Object> jsonSchema = new HashMap<>();
            jsonSchema.put("name", "movie_recommendations");
            jsonSchema.put("strict", true);
            
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            
            Map<String, Object> properties = new HashMap<>();
            Map<String, Object> movieIdsProp = new HashMap<>();
            movieIdsProp.put("type", "array");
            movieIdsProp.put("items", Map.of("type", "integer"));
            properties.put("movieIds", movieIdsProp);
            
            schema.put("properties", properties);
            schema.put("required", List.of("movieIds"));
            schema.put("additionalProperties", false);
            
            jsonSchema.put("schema", schema);
            responseFormat.put("json_schema", jsonSchema);
            body.put("response_format", responseFormat);

            String response = getWebClient().post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(4))
                .block();

            if (response == null) return Collections.emptyList();

            JsonNode root = mapper.readTree(response);
            String content = root.at("/choices/0/message/content").asText("").trim();
            if (content.isEmpty()) return Collections.emptyList();

            List<Integer> ids = new ArrayList<>();
            JsonNode contentNode = mapper.readTree(content);
            if (contentNode.has("movieIds") && contentNode.get("movieIds").isArray()) {
                for (JsonNode node : contentNode.get("movieIds")) {
                    ids.add(node.asInt());
                }
            }
            return ids;

        } catch (Exception e) {
            if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                log.warn("OpenAI API Timeout reached: {}", e.getMessage());
            } else {
                log.warn("OpenAI call failed: {}", e.getMessage());
            }
            return Collections.emptyList();
        }
    }

    public List<Double> getEmbedding(String text) {
        if (!isEnabled() || text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", "text-embedding-3-small");
            body.put("input", text);

            String response = getWebClient().post()
                .uri("/embeddings")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(4))
                .block();

            if (response == null) return Collections.emptyList();

            JsonNode root = mapper.readTree(response);
            JsonNode embeddingNode = root.at("/data/0/embedding");
            if (embeddingNode.isArray()) {
                List<Double> vector = new ArrayList<>();
                for (JsonNode n : embeddingNode) {
                    vector.add(n.asDouble());
                }
                return vector;
            }
        } catch (Exception e) {
            log.warn("Failed to generate embedding: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    public Map<Integer, String> getAIRecommendationsWithReasons(Integer userId, List<Movie> allMovies) {
        if (!isEnabled()) return Collections.emptyMap();

        try {
            List<Rating> ratings = ratingRepository.findByUserUserId(userId);
            List<WatchHistory> history = watchHistoryRepository.findByUserUserIdOrderByWatchedAtAsc(userId);

            if (ratings.isEmpty() && history.isEmpty() && allMovies.isEmpty()) return Collections.emptyMap();

            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            String prompt = buildRecommendationsWithReasonsPrompt(ratings, history, allMovies, isVi);
            if (prompt == null) return Collections.emptyMap();

            String cacheKey = "recs_reasons_" + prompt.hashCode();
            Map<Integer, String> cached = getFromCacheObj(cacheKey);
            if (cached != null) {
                log.debug("Cache hit for recommendations with reasons, user {}", userId);
                return cached;
            }

            Map<Integer, String> result = callOpenAIForRecommendationsWithReasons(prompt);
            if (!result.isEmpty()) {
                putInCacheObj(cacheKey, result);
            }
            return result;
        } catch (Exception e) {
            log.warn("OpenAI recommendations with reasons failed for user {}: {}", userId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String buildRecommendationsWithReasonsPrompt(List<Rating> ratings, List<WatchHistory> history, List<Movie> allMovies, boolean isVi) {
        List<String> loved = ratings.stream()
            .filter(r -> r.getRating() >= 4 && r.getMovie() != null)
            .sorted((a, b) -> Double.compare(b.getRating(), a.getRating()))
            .limit(5)
            .map(r -> r.getMovie().getTitle())
            .collect(Collectors.toList());

        List<String> disliked = ratings.stream()
            .filter(r -> r.getRating() <= 2 && r.getMovie() != null)
            .limit(3)
            .map(r -> r.getMovie().getTitle())
            .collect(Collectors.toList());

        if (loved.isEmpty() && history.isEmpty() && allMovies.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("User Preferences:\n");
        sb.append("- Liked Movies: ").append(loved.isEmpty() ? "None" : String.join(", ", loved)).append("\n");
        if (!disliked.isEmpty()) {
            sb.append("- Disliked Movies: ").append(String.join(", ", disliked)).append("\n");
        }
        sb.append("\nCandidates (Hybrid recommendations with scores and metadata):\n");
        for (Movie m : allMovies) {
            String genres = m.getGenres() != null ? m.getGenres().stream().map(Genre::getGenreName).collect(Collectors.joining(", ")) : "N/A";
            String desc = m.getDescription();
            if (desc != null && desc.length() > 150) {
                desc = desc.substring(0, 147) + "...";
            } else if (desc == null) {
                desc = "No description available.";
            }
            sb.append(String.format(
                "- MovieID: %d | Title: %s | Year: %s | Genres: [%s] | Rating: %.1f | Hybrid Score: %.3f | Desc: %s\n",
                m.getMovieId(), m.getTitle(), m.getReleaseYear() != null ? String.valueOf(m.getReleaseYear()) : "N/A",
                genres, m.getAverageRating(), m.getHybridScore(), desc
            ));
        }
        sb.append("\nTask:\n");
        sb.append("Based on the user's liked and disliked movies, rerank and select the top 5 most suitable movies from the candidates list above.\n");
        if (isVi) {
            sb.append("For each selected movie, provide a personalized explanation (reason) in Vietnamese (20-35 words) explaining why this movie fits their taste. Refer to their favorite genres or similar movies they liked. Example: 'Vì bạn thích Action + Sci-Fi và từng đánh giá cao Interstellar, phim này có cùng nhóm chủ đề không gian, nhịp căng và rating cao.'\n");
        } else {
            sb.append("For each selected movie, provide a personalized explanation (reason) in English (20-35 words) explaining why this movie fits their taste. Refer to their favorite genres or similar movies they liked. Example: 'Since you like Action + Sci-Fi and highly rated Interstellar, this movie shares space themes, intense pacing, and a high rating.'\n");
        }
        sb.append("Return the response matching the specified JSON Schema containing the selected recommendations with reasons.");

        return sb.toString();
    }

    private Map<Integer, String> callOpenAIForRecommendationsWithReasons(String prompt) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", "gpt-4o-mini");
            body.put("max_tokens", 500);
            body.put("temperature", 0.3);
            body.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
            ));

            Map<String, Object> responseFormat = new HashMap<>();
            responseFormat.put("type", "json_schema");
            
            Map<String, Object> jsonSchema = new HashMap<>();
            jsonSchema.put("name", "movie_recommendation_reasons");
            jsonSchema.put("strict", true);
            
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            
            Map<String, Object> properties = new HashMap<>();
            Map<String, Object> recsProp = new HashMap<>();
            recsProp.put("type", "array");
            
            Map<String, Object> recItem = new HashMap<>();
            recItem.put("type", "object");
            Map<String, Object> recItemProps = new HashMap<>();
            recItemProps.put("movieId", Map.of("type", "integer"));
            recItemProps.put("reason", Map.of("type", "string"));
            recItem.put("properties", recItemProps);
            recItem.put("required", List.of("movieId", "reason"));
            recItem.put("additionalProperties", false);
            
            recsProp.put("items", recItem);
            properties.put("recommendations", recsProp);
            
            schema.put("properties", properties);
            schema.put("required", List.of("recommendations"));
            schema.put("additionalProperties", false);
            
            jsonSchema.put("schema", schema);
            responseFormat.put("json_schema", jsonSchema);
            body.put("response_format", responseFormat);

            String response = getWebClient().post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(6))
                .block();

            if (response == null) return Collections.emptyMap();

            JsonNode root = mapper.readTree(response);
            String content = root.at("/choices/0/message/content").asText("").trim();
            if (content.isEmpty()) return Collections.emptyMap();

            Map<Integer, String> reasonsMap = new LinkedHashMap<>();
            JsonNode contentNode = mapper.readTree(content);
            if (contentNode.has("recommendations") && contentNode.get("recommendations").isArray()) {
                for (JsonNode node : contentNode.get("recommendations")) {
                    if (node.has("movieId") && node.has("reason")) {
                        reasonsMap.put(node.get("movieId").asInt(), node.get("reason").asText());
                    }
                }
            }
            return reasonsMap;

        } catch (Exception e) {
            log.warn("OpenAI call for recommendations with reasons failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getFromCache(String key) {
        Object[] entry = cache.get(key);
        if (entry == null) return null;
        long timestamp = (long) entry[1];
        if (System.currentTimeMillis() - timestamp > CACHE_TTL_MS) {
            cache.remove(key);
            return null;
        }
        return (List<T>) entry[0];
    }

    private <T> void putInCache(String key, List<T> value) {
        if (cache.size() > 500) cache.clear();
        cache.put(key, new Object[]{value, System.currentTimeMillis()});
    }

    @SuppressWarnings("unchecked")
    private <T> T getFromCacheObj(String key) {
        Object[] entry = cache.get(key);
        if (entry == null) return null;
        long timestamp = (long) entry[1];
        if (System.currentTimeMillis() - timestamp > CACHE_TTL_MS) {
            cache.remove(key);
            return null;
        }
        return (T) entry[0];
    }

    private void putInCacheObj(String key, Object value) {
        if (cache.size() > 500) cache.clear();
        cache.put(key, new Object[]{value, System.currentTimeMillis()});
    }
}
