package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.*;
import com.example.movierecommendation.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.example.movierecommendation.dto.*;
import com.example.movierecommendation.service.ai.ChatAgentPlan;
import com.example.movierecommendation.service.ai.ChatAgentPlanner;
import com.example.movierecommendation.service.ai.ChatAgentResponder;
import com.example.movierecommendation.service.ai.ChatModelClient;
import com.example.movierecommendation.service.ai.ChatToolExecutor;
import com.example.movierecommendation.service.ai.ChatToolResult;

@Service
public class AIChatService {

    private static final Logger log = LoggerFactory.getLogger(AIChatService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Value("${openai.api.key:}")
    private String apiKey;

    @Autowired private MovieRepository movieRepository;
    @Autowired private GenreRepository genreRepository;
    @Autowired private RatingRepository ratingRepository;
    @Autowired private WatchHistoryRepository watchHistoryRepository;
    @Autowired private AIChatLogRepository aiChatLogRepository;
    @Autowired private AIChatRecommendationItemRepository aiChatRecommendationItemRepository;
    @Autowired private UserPreferenceRepository userPreferenceRepository;
    @Autowired private ChatIntentClassifier intentClassifier;
    @Autowired private ChatHelpService chatHelpService;
    @Autowired private MovieEmbeddingService movieEmbeddingService;
    @Autowired private VideoTimelineRepository videoTimelineRepository;
    @Autowired(required = false) private ChatAgentPlanner chatAgentPlanner;
    @Autowired(required = false) private ChatToolExecutor chatToolExecutor;
    @Autowired(required = false) private ChatAgentResponder chatAgentResponder;
    @Autowired(required = false) private ChatModelClient chatModelClient;
    @Autowired @org.springframework.context.annotation.Lazy private AIChatService self;

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
        if (chatModelClient != null) return chatModelClient.isEnabled();
        String cleanKey = apiKey != null ? apiKey.replace("\"", "").trim() : "";
        return !cleanKey.isEmpty();
    }

    public ChatResponse recommendMovies(User user, String userMessage) {
        if (chatAgentPlanner == null || chatToolExecutor == null || chatAgentResponder == null
                || !chatAgentPlanner.isEnabled()) {
            return recommendMoviesLegacy(user, userMessage);
        }

        List<Movie> candidates = findCandidates(userMessage);
        if (candidates.isEmpty()) {
            candidates = movieRepository.findTopMoviesByRatingCount(PageRequest.of(0, 25));
        }
        candidates = candidates.stream().filter(movie -> movie.getDeletedAt() == null).distinct().limit(30).toList();

        String userContext = buildAgentUserContext(user);
        String candidateCatalog = buildAgentCandidateCatalog(candidates);
        List<Map<String, Object>> recentConversation = loadRecentConversation(user);

        Optional<ChatAgentPlan> planned = chatAgentPlanner.plan(
            userMessage, userContext, candidateCatalog, recentConversation);
        if (planned.isEmpty()) {
            log.info("Agent planner unavailable, using deterministic fallback");
            return recommendMoviesLegacy(user, userMessage);
        }

        ChatAgentPlan plan = planned.get();
        ensureRetrievalToolForMovieIntent(plan, candidates);
        List<ChatToolResult> toolResults = chatToolExecutor.execute(user, plan, candidates);

        List<MovieCardDto> movieCards = assembleMovieCards(toolResults, userContext);
        List<Map<String, Object>> clientActions = toolResults.stream()
            .map(ChatToolResult::getClientAction)
            .filter(Objects::nonNull)
            .toList();
        String reply = chatAgentResponder.respond(userMessage, userContext, plan, toolResults);
        String type = movieCards.isEmpty() ? "TEXT" : "MOVIE_CARDS";

        Map<Integer, Movie> candidateMap = candidates.stream().collect(Collectors.toMap(
            Movie::getMovieId, movie -> movie, (first, ignored) -> first, LinkedHashMap::new));
        toolResults.stream().flatMap(result -> result.getMovies().stream())
            .filter(movie -> movie != null && movie.getMovieId() != null)
            .forEach(movie -> candidateMap.putIfAbsent(movie.getMovieId(), movie));
        if (self != null) {
            self.saveChatLogAndRecommendations(user, userMessage, reply, movieCards, candidateMap);
        } else {
            saveChatLogAndRecommendations(user, userMessage, reply, movieCards, candidateMap);
        }
        return new ChatResponse(type, reply, movieCards, clientActions);
    }

    private ChatResponse recommendMoviesLegacy(User user, String userMessage) {
        ChatIntent intent = intentClassifier.classify(userMessage);
        
        if (intent == ChatIntent.OUT_OF_SCOPE) {
            String reply = chatHelpService.getHelpResponse(ChatIntent.OUT_OF_SCOPE, userMessage);
            if (self != null) self.saveChatLogOnly(user, userMessage, reply);
            else saveChatLogOnly(user, userMessage, reply);
            return new ChatResponse("TEXT", reply, Collections.emptyList(), Collections.emptyList());
        }

        // 1. Find candidate movies in database
        List<Movie> candidates = findCandidates(userMessage);
        
        String normalized = removeAccent(userMessage.toLowerCase().trim());
        boolean isGeneralRecommendation = normalized.contains("goi y") || normalized.contains("de xuat") 
                || normalized.contains("recommend") || normalized.contains("phim nao hay") 
                || normalized.contains("phim hay") || normalized.contains("phim nao hot")
                || normalized.contains("phim hot") || normalized.contains("phim moi")
                || normalized.contains("phim bat hu") || normalized.contains("phim pho bien");
                
        if (candidates.isEmpty() && isGeneralRecommendation) {
            candidates = movieRepository.findTopMoviesByRatingCount(PageRequest.of(0, 25));
        }

        // If candidates are empty and OpenAI is NOT enabled, handle standard fallback
        if (candidates.isEmpty() && !isEnabled()) {
            String reply = String.format("Rất tiếc, hiện tại hệ thống không tìm thấy bộ phim nào phù hợp với yêu cầu hoặc từ khóa '%s'. Bạn hãy thử tìm kiếm bằng tên phim hoặc thể loại khác nhé!", userMessage);
            if (self != null) self.saveChatLogOnly(user, userMessage, reply);
            else saveChatLogOnly(user, userMessage, reply);
            return new ChatResponse("TEXT", reply, Collections.emptyList(), Collections.emptyList());
        }

        // 2. Build user preference context if user is logged in
        String likedMovies = "Không có";
        String watchHistoryText = "Không có";
        String favoriteGenres = "Không có";

        if (user != null) {
            List<Rating> ratings = ratingRepository.findByUserUserId(user.getUserId());
            List<String> highRatings = ratings.stream()
                .filter(r -> r.getRating() >= 4.0 && r.getMovie() != null)
                .limit(5)
                .map(r -> r.getMovie().getTitle() + " (" + r.getRating() + " sao)")
                .collect(Collectors.toList());
            if (!highRatings.isEmpty()) likedMovies = String.join(", ", highRatings);

            List<WatchHistory> history = watchHistoryRepository.findByUserUserIdOrderByWatchedAtDesc(user.getUserId());
            List<String> recentHistory = history.stream()
                .filter(wh -> wh.getMovie() != null)
                .limit(5)
                .map(wh -> wh.getMovie().getTitle())
                .collect(Collectors.toList());
            if (!recentHistory.isEmpty()) watchHistoryText = String.join(", ", recentHistory);

            // Fetch preferred genres from preferences or calculated
            Optional<UserPreference> prefOpt = userPreferenceRepository.findByUserUserId(user.getUserId());
            if (prefOpt.isPresent() && prefOpt.get().getPreferredGenres() != null && !prefOpt.get().getPreferredGenres().isEmpty()) {
                favoriteGenres = prefOpt.get().getPreferredGenres();
            } else {
                Map<String, Double> genreCounts = new HashMap<>();
                for (Rating r : ratings) {
                    if (r.getMovie() != null && r.getMovie().getGenres() != null) {
                        for (Genre g : r.getMovie().getGenres()) {
                            genreCounts.merge(g.getGenreName(), r.getRating(), Double::sum);
                        }
                    }
                }
                List<String> topG = genreCounts.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
                if (!topG.isEmpty()) favoriteGenres = String.join(", ", topG);
            }
        }

        // Format candidate movies for prompt
        StringBuilder candidatesBuilder = new StringBuilder();
        Map<Integer, Movie> candidateMap = new HashMap<>();
        for (Movie m : candidates) {
            candidateMap.put(m.getMovieId(), m);
            String genres = m.getGenres() != null ? m.getGenres().stream().map(Genre::getGenreName).collect(Collectors.joining(", ")) : "N/A";
            candidatesBuilder.append(String.format("- ID: %d | Tên: %s | Năm: %s | Thể loại: [%s] | Rating: %.1f\n", 
                m.getMovieId(), m.getTitle(), m.getReleaseYear() != null ? String.valueOf(m.getReleaseYear()) : "N/A", genres, m.getAverageRating()));
        }

        // 3. Build System/Initial Prompt
        String systemPrompt = String.format(
            "Bạn là Trợ lý AI Movie Assistant thân thiện và thông minh cho website MovieRecommendation (MovieRec).\n" +
            "Nhiệm vụ của bạn là hỗ trợ người dùng bằng tiếng Việt về:\n" +
            "1. Gợi ý phim & Tìm kiếm phim (kết hợp sở thích người dùng và danh sách phim có sẵn trong database).\n" +
            "2. Giải đáp thắc mắc và hướng dẫn sử dụng các tính năng của website MovieRec (như Tài khoản/Profile, Watchlist/Yêu thích, Đánh giá/Review, Lịch sử xem, Điều hướng website).\n" +
            "3. Trò chuyện tự nhiên, chào hỏi thân thiện.\n\n" +
            "THÔNG TIN HƯỚNG DẪN TÍNH NĂNG WEBSITE MOVIEREC:\n" +
            "- Đăng ký: Nhấn 'Đăng ký' (Register) ở thanh điều hướng hoặc 'Create Free Account' ở trang chủ. Điền tên đăng nhập, email, mật khẩu và nhập OTP nhận qua email để hoàn tất kích hoạt.\n" +
            "- Đăng nhập: Nhấn 'Đăng nhập' (Login) ở góc trên bên phải, nhập Email/Username và mật khẩu.\n" +
            "- Quên mật khẩu: Tại trang Đăng nhập, bấm 'Quên mật khẩu?', nhập email để nhận mã OTP khôi phục và đổi mật khẩu mới.\n" +
            "- Đổi mật khẩu: Đăng nhập -> Vào trang 'Profile/Tài khoản' trên thanh điều hướng -> Chọn mục 'Đổi mật khẩu'.\n" +
            "- Sửa thông tin tài khoản: Đăng nhập -> Vào trang 'Profile/Tài khoản' trên thanh điều hướng để sửa đổi thông tin cá nhân.\n" +
            "- Watchlist (Danh sách yêu thích): Thêm phim bằng cách truy cập trang chi tiết phim rồi bấm '+ Watchlist' hoặc 'Yêu thích'. Xóa phim bằng cách bấm lại nút đó ở trang chi tiết phim hoặc vào trang 'Watchlist' từ thanh điều hướng để loại bỏ.\n" +
            "- Đánh giá phim (Rating/Review): Vào trang chi tiết phim, cuộn xuống phần 'Đánh giá & Bình luận' (Ratings & Reviews), chọn số sao mong muốn từ 1 đến 5 và gửi.\n" +
            "- Lịch sử xem: Vào trang 'Profile/Tài khoản' từ thanh điều hướng -> chọn mục 'Lịch sử xem' để theo dõi chi tiết.\n" +
            "- Điều hướng chính: Trang chủ (phim mới, hot), Thanh tìm kiếm (tra cứu phim), Watchlist (phim đã lưu), Profile (xem lịch sử và sửa tài khoản).\n\n" +
            "Thông tin sở thích người dùng nếu có:\n" +
            "- Phim đã đánh giá cao: %s\n" +
            "- Phim đã xem gần đây: %s\n" +
            "- Thể loại thường xem: %s\n\n" +
            "Danh sách phim candidate trong database:\n" +
            "%s\n\n" +
            "QUY TẮC PHẢN HỒI (BẮT BUỘC TRẢ VỀ JSON HỢP LỆ, KHÔNG CHỨA KÝ TỰ THỪA NGOÀI CÚ PHÁP JSON):\n" +
            "{\n" +
            "  \"type\": \"TEXT\" hoặc \"MOVIE_CARDS\",\n" +
            "  \"reply\": \"Lời phản hồi trò chuyện hoặc hướng dẫn chi tiết bằng tiếng Việt...\",\n" +
            "  \"movies\": [\n" +
            "     {\n" +
            "       \"movieId\": 123,\n" +
            "       \"reason\": \"Lý do ngắn gọn vì sao gợi ý phim này\"\n" +
            "     }\n" +
            "  ],\n" +
            "  \"actions\": [\n" +
            "     {\n" +
            "       \"name\": \"ADD_WATCHLIST\" hoặc \"RATE_MOVIE\" hoặc \"FILTER_MOVIES\" hoặc \"VIEW_MOVIE_DETAIL\",\n" +
            "       \"params\": { ... }\n" +
            "     }\n" +
            "  ]\n" +
            "}\n\n" +
            "HƯỚNG DẪN CHỌN TYPE VÀ MOVIES:\n" +
            "- Nếu người dùng chào hỏi, hỏi thông tin tài khoản, hỏi hướng dẫn sử dụng web, trò chuyện thông thường, hoặc tìm kiếm một bộ phim cụ thể nhưng phim đó KHÔNG CÓ trong danh sách database candidates ở trên (ví dụ tìm Doraemon nhưng candidates trống):\n" +
            "  1. Đặt type là \"TEXT\".\n" +
            "  2. Đặt movies là mảng rỗng [].\n" +
            "  3. Trong \"reply\", hãy trò chuyện hoặc hướng dẫn tự nhiên bằng tiếng Việt.\n" +
            "- Nếu người dùng tìm kiếm phim hoặc yêu cầu gợi ý phim mà có các bộ phim phù hợp trong danh sách database candidates ở trên:\n" +
            "  1. Đặt type là \"MOVIE_CARDS\".\n" +
            "  2. Chọn tối đa 5 phim phù hợp nhất từ danh sách candidates ở trên đưa vào danh sách \"movies\". KHÔNG ĐƯỢC TỰ Ý BỊA PHIM HOẶC CHỌN PHIM NGOÀI DANH SÁCH CANDIDATES.\n" +
            "  3. Đặt \"reply\" là lời chào và tóm tắt lý do gợi ý hoặc kết quả tìm kiếm phim của bạn.\n\n" +
            "HƯỚNG DẪN VỀ ACTIONS (CỰC KỲ QUAN TRỌNG):\n" +
            "Nếu người dùng có yêu cầu thực hiện hành động cụ thể trên website, hãy trả về danh sách `actions` tương ứng để hệ thống tự động thực thi thay người dùng:\n" +
            "1. ADD_WATCHLIST: Thêm phim vào danh sách watchlist. Params: `{\"movieId\": integer}`. Ví dụ: 'lưu phim 123 vào watchlist', 'thêm phim Inception vào watchlist'.\n" +
            "2. RATE_MOVIE: Đánh giá phim. Params: `{\"movieId\": integer, \"score\": number (từ 1.0 đến 5.0)}`. Ví dụ: 'đánh giá phim 123 5 sao', 'rate Avatar 4 sao'.\n" +
            "3. VIEW_MOVIE_DETAIL: Mở trang chi tiết/chạy phim. Params: `{\"movieId\": integer}`. Ví dụ: 'xem phim Interstellar', 'mở phim 456', 'play movie Titanic'.\n" +
            "4. FILTER_MOVIES: Lọc danh sách phim. Params: `{\"q\": string, \"genreId\": integer, \"year\": integer, \"minRating\": number, \"sortBy\": string}` (tất cả các trường đều là tùy chọn). Ví dụ: 'lọc phim hành động năm 2020 trở lên', 'tìm phim hài điểm cao'.\n" +
            "Nếu không có hành động nào được yêu cầu, hãy trả về mảng rỗng `[]`.",
            likedMovies, watchHistoryText, favoriteGenres, candidatesBuilder.toString()
        );

        // 4. Build message list (conversational memory)
        List<Map<String, String>> chatMessages = new ArrayList<>();
        chatMessages.add(Map.of("role", "system", "content", systemPrompt));

        if (user != null) {
            List<AIChatLog> history = aiChatLogRepository.findByUserUserIdOrderByCreatedAtDesc(user.getUserId());
            if (history != null && !history.isEmpty()) {
                List<AIChatLog> recentHistory = history.stream().limit(5).collect(Collectors.toList());
                Collections.reverse(recentHistory);
                for (AIChatLog logEntry : recentHistory) {
                    if (logEntry.getMessage() != null && logEntry.getResponseSummary() != null) {
                        chatMessages.add(Map.of("role", "user", "content", logEntry.getMessage()));
                        chatMessages.add(Map.of("role", "assistant", "content", logEntry.getResponseSummary()));
                    }
                }
            }
        }

        // Add the current user request
        chatMessages.add(Map.of("role", "user", "content", userMessage));

        // Call OpenAI API
        String responseContent = null;
        if (isEnabled()) {
            responseContent = callOpenAI(chatMessages);
        }

        List<MovieCardDto> recommendedMovies = new ArrayList<>();
        List<Map<String, Object>> actions = new ArrayList<>();
        String reply = null;
        String responseType = "TEXT";

        if (responseContent != null && !responseContent.trim().isEmpty()) {
            try {
                JsonNode root = mapper.readTree(responseContent);
                if (root.has("type")) {
                    responseType = root.get("type").asText("TEXT");
                }
                if (root.has("reply")) {
                    reply = root.get("reply").asText();
                }
                
                if ("MOVIE_CARDS".equals(responseType) && root.has("movies") && root.get("movies").isArray()) {
                    for (JsonNode mNode : root.get("movies")) {
                        if (mNode.has("movieId")) {
                            int movieId = mNode.get("movieId").asInt();
                            String reason = mNode.has("reason") ? mNode.get("reason").asText() : "";
                            
                            // Validate that movie exists in DB candidates
                            Movie movie = candidateMap.get(movieId);
                            if (movie != null) {
                                List<String> genreNames = movie.getGenres() != null ? 
                                    movie.getGenres().stream().map(Genre::getGenreName).collect(Collectors.toList()) : 
                                    Collections.emptyList();
                                MovieCardDto mDto = new MovieCardDto(
                                    movie.getMovieId(),
                                    movie.getTitle(),
                                    movie.getPosterUrl(),
                                    movie.getReleaseYear(),
                                    genreNames,
                                    movie.getAverageRating(),
                                    reason
                                );
                                recommendedMovies.add(mDto);
                            }
                        }
                    }
                }

                if (root.has("actions") && root.get("actions").isArray()) {
                    for (JsonNode aNode : root.get("actions")) {
                        if (aNode.has("name") && aNode.has("params")) {
                            Map<String, Object> action = new HashMap<>();
                            action.put("name", aNode.get("name").asText());
                            
                            Map<String, Object> params = new HashMap<>();
                            JsonNode pNode = aNode.get("params");
                            if (pNode.has("movieId")) params.put("movieId", pNode.get("movieId").asInt());
                            if (pNode.has("score")) params.put("score", pNode.get("score").asDouble());
                            if (pNode.has("q")) params.put("q", pNode.get("q").asText());
                            if (pNode.has("genreId")) params.put("genreId", pNode.get("genreId").asInt());
                            if (pNode.has("year")) params.put("year", pNode.get("year").asInt());
                            if (pNode.has("minRating")) params.put("minRating", pNode.get("minRating").asDouble());
                            if (pNode.has("sortBy")) params.put("sortBy", pNode.get("sortBy").asText());
                            
                            action.put("params", params);
                            actions.add(action);
                        }
                    }
                }

                if ("MOVIE_CARDS".equals(responseType) && recommendedMovies.isEmpty()) {
                    responseType = "TEXT";
                }
            } catch (Exception e) {
                log.error("Failed to parse OpenAI response: {}", e.getMessage());
                responseContent = null; // trigger fallback
            }
        }

        // Fallback in case OpenAI fails / returns empty / parses wrong / is disabled
        if (responseContent == null || reply == null) {
            log.info("Using fallback recommendation for AI Chat");
            if (intent == ChatIntent.MOVIE_RECOMMENDATION || intent == ChatIntent.MOVIE_SEARCH) {
                if (!candidates.isEmpty()) {
                    responseType = "MOVIE_CARDS";
                    reply = "Dựa trên yêu cầu của bạn, mình đã tìm thấy một số phim phù hợp từ hệ thống:";
                    for (Movie movie : candidates.stream().limit(5).collect(Collectors.toList())) {
                        List<String> genreNames = movie.getGenres() != null ? 
                            movie.getGenres().stream().map(Genre::getGenreName).collect(Collectors.toList()) : 
                            Collections.emptyList();
                        MovieCardDto mDto = new MovieCardDto(
                            movie.getMovieId(),
                            movie.getTitle(),
                            movie.getPosterUrl(),
                            movie.getReleaseYear(),
                            genreNames,
                            movie.getAverageRating(),
                            "Phim phổ biến trong hệ thống phù hợp với từ khóa của bạn."
                        );
                        recommendedMovies.add(mDto);
                    }
                } else {
                    responseType = "TEXT";
                    reply = String.format("Rất tiếc, hiện tại hệ thống không tìm thấy bộ phim nào phù hợp với yêu cầu hoặc từ khóa '%s'. Bạn hãy thử tìm kiếm bằng tên phim hoặc thể loại khác nhé!", userMessage);
                }
            } else {
                responseType = "TEXT";
                reply = chatHelpService.getHelpResponse(intent, userMessage);
            }
        }

        // 5. Save logs to Database
        if (self != null) {
            self.saveChatLogAndRecommendations(user, userMessage, reply, recommendedMovies, candidateMap);
        } else {
            saveChatLogAndRecommendations(user, userMessage, reply, recommendedMovies, candidateMap);
        }

        return new ChatResponse(responseType, reply, recommendedMovies, actions);
    }

    private void ensureRetrievalToolForMovieIntent(ChatAgentPlan plan, List<Movie> candidates) {
        if (plan == null || candidates.isEmpty() || plan.getToolCalls() == null || !plan.getToolCalls().isEmpty()) return;
        String intent = plan.getIntent() == null ? "" : plan.getIntent();
        if (!"MOVIE_SEARCH".equals(intent) && !"MOVIE_RECOMMENDATION".equals(intent)) return;
        String ids = candidates.stream().limit(5).map(movie -> String.valueOf(movie.getMovieId()))
            .collect(Collectors.joining(","));
        String tool = "MOVIE_SEARCH".equals(intent) ? "SEARCH_MOVIES" : "RECOMMEND_MOVIES";
        plan.getToolCalls().add(new ChatAgentPlan.ToolCall(tool, "{\"movieIds\":[" + ids + "]}"));
    }

    private String buildAgentUserContext(User user) {
        if (user == null) return "Khách chưa đăng nhập; không được thực hiện thao tác thay đổi dữ liệu.";
        List<Rating> ratings = ratingRepository.findByUserUserId(user.getUserId());
        List<String> liked = ratings.stream().filter(rating -> rating.getRating() >= 4 && rating.getMovie() != null)
            .limit(5).map(rating -> rating.getMovie().getTitle() + " (" + rating.getRating() + " sao)").toList();
        List<String> disliked = ratings.stream().filter(rating -> rating.getRating() <= 2 && rating.getMovie() != null)
            .limit(3).map(rating -> rating.getMovie().getTitle()).toList();
        List<String> history = watchHistoryRepository.findByUserUserIdOrderByWatchedAtDesc(user.getUserId()).stream()
            .filter(item -> item.getMovie() != null).limit(5).map(item -> item.getMovie().getTitle()).toList();
        String preferences = userPreferenceRepository.findByUserUserId(user.getUserId())
            .map(value -> "thích=" + value.getPreferredGenres() + ", không thích=" + value.getDislikedGenres()
                + ", rating tối thiểu=" + value.getMinRating())
            .orElse("chưa thiết lập");
        return "User đã đăng nhập (id=" + user.getUserId() + "). Phim thích: "
            + (liked.isEmpty() ? "chưa có" : String.join(", ", liked))
            + ". Phim không thích: " + (disliked.isEmpty() ? "chưa có" : String.join(", ", disliked))
            + ". Xem gần đây: " + (history.isEmpty() ? "chưa có" : String.join(", ", history))
            + ". Preferences: " + preferences + ".";
    }

    private String buildAgentCandidateCatalog(List<Movie> candidates) {
        if (candidates == null || candidates.isEmpty()) return "Không có phim phù hợp trong database.";
        return candidates.stream().map(movie -> {
            String genres = movie.getGenres() == null ? "N/A" : movie.getGenres().stream()
                .map(Genre::getGenreName).collect(Collectors.joining(", "));
            String description = movie.getDescription() == null ? "N/A" : movie.getDescription().replaceAll("\\s+", " ");
            if (description.length() > 180) description = description.substring(0, 177) + "...";
            return String.format("ID=%d | %s | year=%s | genres=%s | rating=%.1f | description=%s",
                movie.getMovieId(), movie.getTitle(), movie.getReleaseYear() == null ? "N/A" : movie.getReleaseYear(),
                genres, movie.getAverageRating(), description);
        }).collect(Collectors.joining("\n"));
    }

    private List<Map<String, Object>> loadRecentConversation(User user) {
        if (user == null) return Collections.emptyList();
        List<AIChatLog> history = aiChatLogRepository.findByUserUserIdOrderByCreatedAtDesc(user.getUserId());
        if (history == null || history.isEmpty()) return Collections.emptyList();
        List<AIChatLog> recent = new ArrayList<>(history.stream().limit(5).toList());
        Collections.reverse(recent);
        List<Map<String, Object>> messages = new ArrayList<>();
        for (AIChatLog entry : recent) {
            if (entry.getMessage() != null) messages.add(Map.of("role", "user", "content", entry.getMessage()));
            if (entry.getResponseSummary() != null) messages.add(Map.of("role", "assistant", "content", entry.getResponseSummary()));
        }
        return messages;
    }

    private List<MovieCardDto> assembleMovieCards(List<ChatToolResult> results, String userContext) {
        Map<Integer, MovieCardDto> cards = new LinkedHashMap<>();
        if (results == null) return Collections.emptyList();
        for (ChatToolResult result : results) {
            if (!result.isSuccess()) continue;
            for (Movie movie : result.getMovies()) {
                if (movie == null || movie.getMovieId() == null || cards.size() >= 5) continue;
                List<String> genres = movie.getGenres() == null ? Collections.emptyList()
                    : movie.getGenres().stream().map(Genre::getGenreName).toList();
                String genreText = genres.isEmpty() ? "nội dung" : String.join(", ", genres.stream().limit(2).toList());
                String reason = userContext.startsWith("Khách")
                    ? String.format("Phim %s trong database, rating %.1f/5.", genreText, movie.getAverageRating())
                    : String.format("Phù hợp với ngữ cảnh và sở thích của bạn; thuộc nhóm %s, rating %.1f/5.", genreText, movie.getAverageRating());
                cards.putIfAbsent(movie.getMovieId(), new MovieCardDto(movie.getMovieId(), movie.getTitle(),
                    movie.getPosterUrl(), movie.getReleaseYear(), genres, movie.getAverageRating(), reason));
            }
        }
        return new ArrayList<>(cards.values());
    }

    private List<Movie> findCandidates(String message) {
        String msgLower = message.toLowerCase();
        Set<Genre> matchedGenres = new HashSet<>();
        List<Genre> allGenres = genreRepository.findAll();
        
        // Genre keywords mapping
        Map<String, List<String>> genreKeywords = new HashMap<>();
        genreKeywords.put("Action", List.of("hành động", "action", "đánh nhau", "bắn nhau", "võ thuật"));
        genreKeywords.put("Adventure", List.of("phiêu lưu", "adventure", "thám hiểm", "khám phá"));
        genreKeywords.put("Animation", List.of("hoạt hình", "animation", "anime", "hoạt họa"));
        genreKeywords.put("Comedy", List.of("hài", "comedy", "vui", "hài hước", "thư giãn", "gây cười"));
        genreKeywords.put("Romance", List.of("tình cảm", "lãng mạn", "romance", "tình yêu", "ngọt ngào"));
        genreKeywords.put("Horror", List.of("kinh dị", "horror", "ma", "quỷ", "sợ", "rùng rợn"));
        genreKeywords.put("Science Fiction", List.of("viễn tưởng", "sci-fi", "khoa học viễn tưởng", "vũ trụ", "tương lai"));
        genreKeywords.put("Thriller", List.of("giật gân", "kịch tính", "thriller", "hồi hộp"));
        genreKeywords.put("Mystery", List.of("bí ẩn", "mystery", "trinh thám", "phá án"));
        genreKeywords.put("Drama", List.of("chính kịch", "drama", "tâm lý", "đời thường"));

        for (Genre g : allGenres) {
            List<String> keywords = genreKeywords.get(g.getGenreName());
            if (keywords != null) {
                for (String kw : keywords) {
                    if (msgLower.contains(kw)) {
                        matchedGenres.add(g);
                        break;
                    }
                }
            }
        }

        List<Movie> matchedMovies = new ArrayList<>();
        if (!matchedGenres.isEmpty()) {
            List<Integer> genreIds = matchedGenres.stream().map(Genre::getGenreId).collect(Collectors.toList());
            matchedMovies.addAll(movieRepository.findByGenreIdsAndNotInIds(genreIds, List.of(-1), PageRequest.of(0, 30)));
        }

        // Add movies matching title search / vector search
        List<Movie> textSearch = movieRepository.searchByTitleOrGenre(message);
        if (textSearch != null) {
            matchedMovies.addAll(textSearch);
        }

        // Add semantic embedding search results if enabled
        if (isEnabled() && movieEmbeddingService != null) {
            List<Movie> semanticSearch = movieEmbeddingService.searchSemantic(message, 30);
            if (semanticSearch != null) {
                matchedMovies.addAll(semanticSearch);
            }
        }

        // Deduplicate and filter deleted movies
        return matchedMovies.stream()
            .distinct()
            .filter(m -> m.getDeletedAt() == null)
            .limit(30)
            .collect(Collectors.toList());
    }

    private String callOpenAI(List<Map<String, String>> messages) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", "gpt-4o-mini");
            body.put("max_tokens", 800);
            body.put("temperature", 0.7);
            body.put("messages", messages);

            // Structured Outputs JSON Schema
            Map<String, Object> responseFormat = new HashMap<>();
            responseFormat.put("type", "json_schema");
            
            Map<String, Object> jsonSchema = new HashMap<>();
            jsonSchema.put("name", "chat_response");
            jsonSchema.put("strict", true);
            
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            
            Map<String, Object> properties = new HashMap<>();
            
            Map<String, Object> typeProp = new HashMap<>();
            typeProp.put("type", "string");
            typeProp.put("enum", List.of("TEXT", "MOVIE_CARDS"));
            properties.put("type", typeProp);
            
            Map<String, Object> replyProp = new HashMap<>();
            replyProp.put("type", "string");
            properties.put("reply", replyProp);
            
            Map<String, Object> moviesProp = new HashMap<>();
            moviesProp.put("type", "array");
            
            Map<String, Object> movieItem = new HashMap<>();
            movieItem.put("type", "object");
            
            Map<String, Object> itemProps = new HashMap<>();
            itemProps.put("movieId", Map.of("type", "integer"));
            itemProps.put("reason", Map.of("type", "string"));
            movieItem.put("properties", itemProps);
            movieItem.put("required", List.of("movieId", "reason"));
            movieItem.put("additionalProperties", false);
            
            moviesProp.put("items", movieItem);
            properties.put("movies", moviesProp);

            // Add actions property
            Map<String, Object> actionsProp = new HashMap<>();
            actionsProp.put("type", "array");

            Map<String, Object> actionItem = new HashMap<>();
            actionItem.put("type", "object");

            Map<String, Object> actionItemProps = new HashMap<>();
            actionItemProps.put("name", Map.of("type", "string", "enum", List.of("ADD_WATCHLIST", "RATE_MOVIE", "FILTER_MOVIES", "VIEW_MOVIE_DETAIL")));

            Map<String, Object> paramsObj = new HashMap<>();
            paramsObj.put("type", "object");
            Map<String, Object> paramsProps = new HashMap<>();
            paramsProps.put("movieId", Map.of("type", "integer"));
            paramsProps.put("score", Map.of("type", "number"));
            paramsProps.put("q", Map.of("type", "string"));
            paramsProps.put("genreId", Map.of("type", "integer"));
            paramsProps.put("year", Map.of("type", "integer"));
            paramsProps.put("minRating", Map.of("type", "number"));
            paramsProps.put("sortBy", Map.of("type", "string"));
            paramsObj.put("properties", paramsProps);
            paramsObj.put("additionalProperties", false);

            actionItemProps.put("params", paramsObj);
            actionItem.put("properties", actionItemProps);
            actionItem.put("required", List.of("name", "params"));
            actionItem.put("additionalProperties", false);

            actionsProp.put("items", actionItem);
            properties.put("actions", actionsProp);
            
            schema.put("properties", properties);
            schema.put("required", List.of("type", "reply", "movies", "actions"));
            schema.put("additionalProperties", false);
            
            jsonSchema.put("schema", schema);
            responseFormat.put("json_schema", jsonSchema);
            body.put("response_format", responseFormat);

            if (chatModelClient != null) {
                List<Map<String, Object>> providerMessages = messages.stream()
                    .map(message -> (Map<String, Object>) new LinkedHashMap<String, Object>(message))
                    .toList();
                return chatModelClient.complete(providerMessages, responseFormat, 800, 0.7);
            }

            String response = getWebClient().post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(6))
                .block();

            if (response == null) return null;

            JsonNode root = mapper.readTree(response);
            return root.at("/choices/0/message/content").asText("").trim();
        } catch (Exception e) {
            log.warn("AI Chat API call failed: {}", e.getMessage());
            return null;
        }
    }

    public String chatAboutVideo(User user, Movie movie, String userMessage) {
        if (userMessage == null) userMessage = "";
        String msgLower = removeAccent(userMessage.toLowerCase().trim());
        boolean isVideoQuery = msgLower.contains("tom tat") || msgLower.contains("summary") 
                || msgLower.contains("timeline") || msgLower.contains("video") 
                || msgLower.contains("clip") || msgLower.contains("trailer")
                || msgLower.contains("combat") || msgLower.contains("danh nhau")
                || msgLower.contains("chien dau") || msgLower.contains("fight");

        if (!isVideoQuery && intentClassifier != null && chatHelpService != null) {
            ChatIntent intent = intentClassifier.classify(userMessage);
            if (intent == ChatIntent.OUT_OF_SCOPE) {
                return chatHelpService.getHelpResponse(ChatIntent.OUT_OF_SCOPE, userMessage);
            }
        }

        String msgLowerRaw = userMessage.toLowerCase().trim();
        boolean isSummaryRequest = msgLowerRaw.contains("tóm tắt") || msgLowerRaw.contains("tom tat") 
                || msgLowerRaw.contains("summary") || msgLowerRaw.contains("timeline");

        // Load actual timelines from repository
        List<VideoTimeline> timelines = videoTimelineRepository.findByMovieMovieIdOrderByTimestampSecondsAsc(movie.getMovieId());
        
        StringBuilder timelineText = new StringBuilder();
        if (timelines != null && !timelines.isEmpty()) {
            timelineText.append("DƯỚI ĐÂY LÀ DÒNG THỜI GIAN & TRANSCRIPT THỰC TẾ CỦA VIDEO TRAILER PHIM NÀY:\n");
            for (VideoTimeline vt : timelines) {
                int min = vt.getTimestampSeconds() / 60;
                int sec = vt.getTimestampSeconds() % 60;
                String tsStr = String.format("[%02d:%02d]", min, sec);
                timelineText.append(String.format("- Mốc %s: %s", tsStr, vt.getEventDescription()));
                if (vt.getTranscriptText() != null && !vt.getTranscriptText().trim().isEmpty()) {
                    timelineText.append(String.format(" | Lời thoại: \"%s\"", vt.getTranscriptText()));
                }
                timelineText.append("\n");
            }
            timelineText.append("\nHƯỚNG DẪN CỰC KỲ QUAN TRỌNG: Bạn PHẢI trả lời dựa trên dữ liệu dòng thời gian thực tế này. Nếu người dùng hỏi về phân cảnh cụ thể tại mốc thời gian nào, hoặc yêu cầu tóm tắt video theo dòng thời gian, bạn hãy chỉ ra mốc thời gian chính xác dạng [MM:SS] từ danh sách trên. Tuyệt đối không được tự bịa đặt các mốc thời gian ảo ngoài danh sách trên.");
        } else {
            timelineText.append("LƯU Ý QUAN TRỌNG: Hiện tại hệ thống không có dữ liệu transcript hoặc mốc thời gian (timeline) thực tế của video/trailer này. Do đó, nếu người dùng hỏi về các phân cảnh cụ thể tại mốc thời gian nào, hoặc yêu cầu tóm tắt video theo dòng thời gian, bạn phải lịch sự thông báo là hệ thống chưa hỗ trợ dữ liệu timeline/transcript cho video này và không được tự tiện bịa đặt các mốc thời gian ảo.");
        }

        if (isEnabled()) {
            try {
                String systemPrompt = String.format(
                    "Bạn là trợ lý AI thân thiện cho trang web MovieRecommendation.\n" +
                    "Bạn đang cùng người dùng xem trailer/phim '%s' (Năm: %d, Thể loại: %s, Mô tả: %s).\n" +
                    "Người dùng gửi tin nhắn: '%s'\n\n" +
                    "YÊU CẦU:\n" +
                    "1. Trả lời câu hỏi của người dùng bằng tiếng Việt, thân thiện, tự nhiên.\n" +
                    "2. Nếu câu hỏi liên quan đến nội dung, thông tin, phân tích phim:\n" +
                    "   - Hãy trả lời chính xác dựa trên thông tin phim.\n" +
                    "   - %s\n" +
                    "   - Thay vào đó, hãy tóm tắt nội dung và cốt truyện của bộ phim dựa trên phần mô tả phim được cung cấp.\n" +
                    "3. Nếu họ chào hỏi hoặc hỏi về tính năng website, hãy trả lời và hướng dẫn họ một cách thân thiện (ví dụ cách đăng ký/đăng nhập, cách đổi mật khẩu, cách đánh giá phim, cách quản lý Watchlist...).\n" +
                    "4. KHÔNG sử dụng các định dạng markdown phức tạp khác ngoại trừ danh sách và in đậm. Đảm bảo mốc thời gian có dạng [MM:SS] hoặc MM:SS.",
                    movie.getTitle(),
                    movie.getReleaseYear() != null ? movie.getReleaseYear() : 2026,
                    movie.getGenres() != null ? movie.getGenres().stream().map(Genre::getGenreName).collect(Collectors.joining(", ")) : "Chưa rõ",
                    movie.getDescription() != null ? movie.getDescription() : "Không có mô tả.",
                    userMessage,
                    timelineText.toString()
                );

                if (chatModelClient != null) {
                    String content = chatModelClient.complete(
                        List.of(Map.of("role", "user", "content", systemPrompt)), null, 500, 0.7);
                    if (content != null && !content.isEmpty()) return content;
                } else {
                    Map<String, Object> body = new HashMap<>();
                    body.put("model", "gpt-4o-mini");
                    body.put("max_tokens", 500);
                    body.put("temperature", 0.7);
                    body.put("messages", List.of(Map.of("role", "user", "content", systemPrompt)));

                    String response = getWebClient().post()
                        .uri("/chat/completions")
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(6))
                        .block();

                    if (response != null) {
                        JsonNode root = mapper.readTree(response);
                        String content = root.at("/choices/0/message/content").asText("").trim();
                        if (!content.isEmpty()) return content;
                    }
                }
            } catch (Exception e) {
                log.warn("OpenAI video chat failed, falling back: {}", e.getMessage());
            }
        }

        // Fallback or Rule-based response
        if (isSummaryRequest) {
            String title = movie.getTitle();
            if (timelines != null && !timelines.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Dòng thời gian tóm tắt video **%s**:\n\n", title));
                for (VideoTimeline vt : timelines) {
                    int min = vt.getTimestampSeconds() / 60;
                    int sec = vt.getTimestampSeconds() % 60;
                    String tsStr = String.format("[%02d:%02d]", min, sec);
                    sb.append(String.format("- **%s**: %s\n", tsStr, vt.getEventDescription()));
                }
                sb.append("\nBạn có thể nhấn vào các mốc thời gian để xem phân cảnh đó!");
                return sb.toString();
            }
            return String.format(
                "Hiện tại hệ thống chưa có dữ liệu transcript hoặc dòng thời gian chính xác cho video của bộ phim **%s**.\n\n" +
                "Tuy nhiên, dựa trên mô tả, bộ phim xoay quanh: %s",
                title,
                movie.getDescription() != null ? movie.getDescription() : "Nội dung phim chưa được cập nhật chi tiết."
            );
        } else {
            if (intentClassifier != null && chatHelpService != null) {
                ChatIntent intent = intentClassifier.classify(userMessage);
                if (intent == ChatIntent.GREETING || intent == ChatIntent.ACCOUNT_HELP 
                        || intent == ChatIntent.WATCHLIST_HELP || intent == ChatIntent.RATING_HELP 
                        || intent == ChatIntent.HISTORY_HELP || intent == ChatIntent.SITE_NAVIGATION) {
                    return chatHelpService.getHelpResponse(intent, userMessage);
                }
            }
            return getSmartFallbackResponse(movie, userMessage);
        }
    }

    private String getSmartFallbackResponse(Movie movie, String userMessage) {
        String msgLower = userMessage.toLowerCase().trim();
        String title = movie.getTitle();
        String genres = movie.getGenres() != null ? movie.getGenres().stream().map(Genre::getGenreName).collect(Collectors.joining(", ")) : "Chưa rõ";
        
        // 1. Check for similar movies request
        if (msgLower.contains("tương tự") || msgLower.contains("tuong tu") 
                || msgLower.contains("giống") || msgLower.contains("giong")
                || msgLower.contains("khác") || msgLower.contains("khac")
                || msgLower.contains("đề xuất") || msgLower.contains("de xuat")
                || msgLower.contains("recommend")) {
            
            List<Integer> genreIds = movie.getGenres() != null ? 
                movie.getGenres().stream().map(Genre::getGenreId).collect(Collectors.toList()) : 
                Collections.emptyList();
            
            List<Movie> similar;
            if (!genreIds.isEmpty()) {
                similar = movieRepository.findByGenreIdsAndNotInIds(genreIds, List.of(movie.getMovieId()), PageRequest.of(0, 5));
            } else {
                similar = Collections.emptyList();
            }
            
            if (!similar.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Dưới đây là một số phim tương tự cùng thể loại với **%s** mà bạn có thể quan tâm:\n\n", title));
                for (Movie m : similar) {
                    sb.append(String.format("- **%s** (%s) - Rating: %.1f\n", m.getTitle(), m.getReleaseYear() != null ? String.valueOf(m.getReleaseYear()) : "Chưa rõ", m.getAverageRating()));
                }
                sb.append("\nBạn có thể nhấn vào các bộ phim này trên trang danh sách hoặc tìm kiếm chúng để xem thêm!");
                return sb.toString();
            } else {
                return String.format("Hiện tại hệ thống chưa tìm thấy bộ phim nào tương tự với **%s**. Bạn hãy thử xem thêm các bộ phim khác cùng thể loại *%s* nhé!", title, genres);
            }
        }
        
        // 1.5. Check for combat / action / fight scenes / timeline
        if (msgLower.contains("combat") || msgLower.contains("hành động") 
                || msgLower.contains("hanh dong") || msgLower.contains("đánh nhau")
                || msgLower.contains("danh nhau") || msgLower.contains("chiến đấu")
                || msgLower.contains("chien dau") || msgLower.contains("fight")
                || msgLower.contains("phân cảnh") || msgLower.contains("phan canh")
                || msgLower.contains("mốc thời gian") || msgLower.contains("moc thoi gian")
                || msgLower.contains("timeline") || msgLower.contains("timestamp")) {
            
            List<VideoTimeline> fallbackTimelines = videoTimelineRepository.findByMovieMovieIdOrderByTimestampSecondsAsc(movie.getMovieId());
            if (fallbackTimelines != null && !fallbackTimelines.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Các phân cảnh nổi bật trong video/trailer phim **%s**:\n\n", title));
                for (VideoTimeline vt : fallbackTimelines) {
                    int min = vt.getTimestampSeconds() / 60;
                    int sec = vt.getTimestampSeconds() % 60;
                    String tsStr = String.format("[%02d:%02d]", min, sec);
                    sb.append(String.format("- **%s**: %s\n", tsStr, vt.getEventDescription()));
                }
                sb.append("\nBạn có thể nhấn vào các mốc thời gian ở trên để tua nhanh đến đoạn đó!");
                return sb.toString();
            } else {
                return String.format(
                    "Hệ thống chưa có dữ liệu timeline/transcript chính xác cho video của phim **%s**. " +
                    "Mình không thể cung cấp mốc thời gian khi chưa có dữ liệu thực.",
                    title
                );
            }
        }
        
        // 2. Check for description / overview / content
        if (msgLower.contains("mô tả") || msgLower.contains("mo ta") 
                || msgLower.contains("nội dung") || msgLower.contains("noi dung")
                || msgLower.contains("cốt truyện") || msgLower.contains("cot truyen")
                || msgLower.contains("about") || msgLower.contains("description")) {
            return String.format("Bộ phim **%s** có nội dung tóm tắt như sau:\n\n%s", title, 
                movie.getDescription() != null ? movie.getDescription() : "Không có mô tả chi tiết.");
        }
        
        // 3. Check for release year
        if (msgLower.contains("năm") || msgLower.contains("nam") 
                || msgLower.contains("sản xuất") || msgLower.contains("san xuat")
                || msgLower.contains("chiếu") || msgLower.contains("chieu")
                || msgLower.contains("year") || msgLower.contains("release")) {
            return String.format("Phim **%s** được phát hành vào năm **%s**.", title, movie.getReleaseYear() != null ? String.valueOf(movie.getReleaseYear()) : "Chưa rõ");
        }
        
        // 4. Check for rating
        if (msgLower.contains("đánh giá") || msgLower.contains("danh gia") 
                || msgLower.contains("điểm") || msgLower.contains("diem")
                || msgLower.contains("rating") || msgLower.contains("sao")) {
            return String.format("Phim **%s** hiện có điểm đánh giá trung bình là **%.1f/5** sao trên hệ thống.", title, movie.getAverageRating());
        }

        // 5. Check for genres
        if (msgLower.contains("thể loại") || msgLower.contains("the loai") || msgLower.contains("genre")) {
            return String.format("Phim **%s** thuộc các thể loại: *%s*.", title, genres);
        }

        // Default movie QA fallback
        return String.format(
            "Chào bạn! Bộ phim **%s** thuộc thể loại *%s*. %s\n\n" +
            "Hệ thống chưa có timeline/transcript chính xác, nên mình sẽ không tự tạo các mốc thời gian. " +
            "Bạn có thể hỏi mình về mô tả, năm phát hành, thể loại hoặc rating có trong database.",
            title, genres,
            movie.getDescription() == null ? "Thông tin mô tả chưa được cập nhật." : movie.getDescription()
        );
    }

    private String removeAccent(String s) {
        if (s == null) return null;
        String temp = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String result = pattern.matcher(temp).replaceAll("");
        return result.replaceAll("[đĐ]", "d");
    }

    @Transactional
    public void saveChatLogOnly(User user, String userMessage, String reply) {
        try {
            AIChatLog chatLog = new AIChatLog();
            chatLog.setUser(user);
            chatLog.setMessage(userMessage);
            chatLog.setResponseSummary(reply);
            chatLog.setCreatedAt(LocalDateTime.now());
            aiChatLogRepository.save(chatLog);
        } catch (Exception e) {
            log.warn("Failed to save AI Chat log: {}", e.getMessage());
        }
    }

    @Transactional
    public void saveChatLogAndRecommendations(User user, String userMessage, String reply, 
                                             List<MovieCardDto> recommendedMovies, 
                                             Map<Integer, Movie> candidateMap) {
        try {
            AIChatLog chatLog = new AIChatLog();
            chatLog.setUser(user);
            chatLog.setMessage(userMessage);
            chatLog.setResponseSummary(reply);
            chatLog.setCreatedAt(LocalDateTime.now());
            AIChatLog savedLog = aiChatLogRepository.save(chatLog);

            List<AIChatRecommendationItem> items = new ArrayList<>();
            for (int i = 0; i < recommendedMovies.size(); i++) {
                MovieCardDto mDto = recommendedMovies.get(i);
                Movie movie = candidateMap.get(mDto.getMovieId());
                if (movie != null) {
                    AIChatRecommendationItem item = new AIChatRecommendationItem();
                    item.setChatLog(savedLog);
                    item.setMovie(movie);
                    item.setReason(mDto.getReason());
                    item.setRankOrder(i + 1);
                    items.add(item);
                }
            }
            aiChatRecommendationItemRepository.saveAll(items);
        } catch (Exception e) {
            log.warn("Failed to save AI Chat log: {}", e.getMessage());
        }
    }
}
