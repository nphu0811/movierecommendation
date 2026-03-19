package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.entity.Rating;
import com.example.movierecommendation.entity.WatchHistory;
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

    // WebClient singleton - không tạo mới mỗi request
    private WebClient webClient;

    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        }
        return webClient;
    }

    public boolean isEnabled() {
        boolean enabled = apiKey != null && !apiKey.trim().isEmpty();
        if (enabled && log.isDebugEnabled()) {
             log.debug("OpenAI API is enabled. Key: {}***", apiKey.substring(0, Math.min(4, apiKey.length())));
        }
        return enabled;
    }

    public List<String> getAIRecommendedTitles(Integer userId, List<Movie> allMovies) {
        if (!isEnabled()) return Collections.emptyList();

        try {
            List<Rating> ratings = ratingRepository.findByUserUserId(userId);
            List<WatchHistory> history = watchHistoryRepository.findByUserUserIdOrderByWatchedAtDesc(userId);

            if (ratings.isEmpty() && history.isEmpty()) return Collections.emptyList();

            // Build prompt NGẮN - chỉ lấy những gì cần thiết
            String prompt = buildCompactPrompt(ratings, history, allMovies);
            if (prompt == null) return Collections.emptyList();

            // Check cache trước khi gọi API
            String cacheKey = String.valueOf(prompt.hashCode());
            List<String> cached = getFromCache(cacheKey);
            if (cached != null) {
                log.debug("Cache hit for user {}", userId);
                return cached;
            }

            // Gọi OpenAI bằng WebClient (non-blocking với timeout)
            List<String> result = callOpenAIAsync(prompt);

            // Lưu vào cache
            if (!result.isEmpty()) {
                putInCache(cacheKey, result);
            }
            return result;

        } catch (Exception e) {
            log.warn("OpenAI recommendation failed for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    // Prompt ngắn gọn - giảm 60-70% token so với cũ
    private String buildCompactPrompt(List<Rating> ratings, List<WatchHistory> history, List<Movie> allMovies) {
        // Lấy top 5 phim được rated cao nhất
        List<String> loved = ratings.stream()
            .filter(r -> r.getRating() >= 4 && r.getMovie() != null)
            .sorted((a, b) -> b.getRating() - a.getRating())
            .limit(5)
            .map(r -> r.getMovie().getTitle())
            .collect(Collectors.toList());

        // Lấy top 3 phim disliked
        List<String> disliked = ratings.stream()
            .filter(r -> r.getRating() <= 2 && r.getMovie() != null)
            .limit(3)
            .map(r -> r.getMovie().getTitle())
            .collect(Collectors.toList());

        if (loved.isEmpty() && history.isEmpty()) return null;

        // Build set phim đã xem để loại ra khỏi candidates
        Set<Integer> watchedIds = new HashSet<>();
        history.forEach(wh -> { if (wh.getMovie() != null) watchedIds.add(wh.getMovie().getMovieId()); });
        ratings.forEach(r -> { if (r.getMovie() != null) watchedIds.add(r.getMovie().getMovieId()); });

        // Chỉ gửi 20 phim candidates (đủ để AI chọn), loại phim đã xem
        List<String> candidates = allMovies.stream()
            .filter(m -> !watchedIds.contains(m.getMovieId()))
            .limit(20)
            .map(Movie::getTitle)
            .collect(Collectors.toList());

        if (candidates.isEmpty()) return null;

        // Prompt cực ngắn - đủ context, không thừa
        StringBuilder sb = new StringBuilder();
        sb.append("Liked: ").append(String.join(", ", loved)).append("\n");
        if (!disliked.isEmpty()) sb.append("Disliked: ").append(String.join(", ", disliked)).append("\n");
        sb.append("Pick 5 from: ").append(String.join(", ", candidates)).append("\n");
        sb.append("Return JSON array only: [\"title1\",\"title2\",\"title3\",\"title4\",\"title5\"]");

        return sb.toString();
    }

    // Non-blocking call dùng WebClient với timeout
    private List<String> callOpenAIAsync(String prompt) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", "gpt-3.5-turbo");
            body.put("max_tokens", 100); // Giới hạn output - đủ cho 5 tên phim
            body.put("temperature", 0.5); // Giảm randomness -> nhanh hơn, ổn định hơn
            body.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
            ));

            String response = getWebClient().post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10)) // Timeout 10s
                .block(); // Block ở đây vì controller dùng MVC (không phải Reactive)

            if (response == null) return Collections.emptyList();

            JsonNode root = mapper.readTree(response);
            String content = root.at("/choices/0/message/content").asText("").trim();
            if (content.isEmpty()) return Collections.emptyList();

            return parseTitleArray(content);

        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("OpenAI API Timeout reached: {}", e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("OpenAI call failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> getFromCache(String key) {
        Object[] entry = cache.get(key);
        if (entry == null) return null;
        long timestamp = (long) entry[1];
        if (System.currentTimeMillis() - timestamp > CACHE_TTL_MS) {
            cache.remove(key);
            return null;
        }
        //noinspection unchecked
        return (List<String>) entry[0];
    }

    private void putInCache(String key, List<String> value) {
        // Giới hạn cache size tránh OOM
        if (cache.size() > 500) cache.clear();
        cache.put(key, new Object[]{value, System.currentTimeMillis()});
    }

    private List<String> parseTitleArray(String json) {
        List<String> titles = new ArrayList<>();
        try {
            // Tìm JSON array trong response
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
            JsonNode arr = mapper.readTree(json);
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    String title = node.asText().trim();
                    if (!title.isEmpty()) titles.add(title);
                }
            }
        } catch (Exception e) {
            log.debug("JSON parse fallback: {}", e.getMessage());
        }
        return titles;
    }
}