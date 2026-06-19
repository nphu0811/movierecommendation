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
        if (chatAgentPlanner == null || chatToolExecutor == null || chatAgentResponder == null) {
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
        ChatAgentPlan plan = planned.isPresent()
            ? planned.get()
            : buildDeterministicAgentPlan(userMessage, candidates);
        if (planned.isEmpty()) log.info("Model planner unavailable, using fallback router with the same tool executor");
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

    private ChatAgentPlan buildDeterministicAgentPlan(String userMessage, List<Movie> candidates) {
        boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
        ChatAgentPlan plan = new ChatAgentPlan();
        ChatIntent intent = intentClassifier.classify(userMessage);
        String normalized = removeAccent(userMessage.toLowerCase(Locale.ROOT));
        String movieQuery = extractMovieQuery(userMessage, candidates);

        if (normalized.contains("timeline") || normalized.contains("timestamp")
                || normalized.contains("transcript") || normalized.contains("moc thoi gian")) {
            plan.setIntent("VIDEO_QA");
            if (movieQuery.isBlank()) {
                plan.setMissingInfo(isVi ? "tên phim bạn muốn kiểm tra timeline" : "the movie title you want to check the timeline for");
            } else {
                plan.getToolCalls().add(new ChatAgentPlan.ToolCall("GET_MOVIE_DETAIL",
                    jsonArguments(Map.of("query", movieQuery))));
            }
            return plan;
        }

        if (intent == ChatIntent.WATCHLIST_HELP && (normalized.contains("luu phim")
                || normalized.contains("them phim") || normalized.contains("add watchlist"))) {
            plan.setIntent("USER_ACTION");
            if (movieQuery.isBlank()) {
                plan.setMissingInfo(isVi ? "tên phim bạn muốn thêm vào Watchlist" : "the movie title you want to add to your Watchlist");
            } else {
                plan.getToolCalls().add(new ChatAgentPlan.ToolCall("ADD_WATCHLIST",
                    jsonArguments(Map.of("query", movieQuery))));
            }
            return plan;
        }

        if (intent == ChatIntent.RATING_HELP && (normalized.contains("danh gia")
                || normalized.contains("cho diem") || normalized.contains("rate"))) {
            Double score = extractRatingScore(normalized);
            plan.setIntent("USER_ACTION");
            if (movieQuery.isBlank() || score == null) {
                plan.setMissingInfo(isVi ? "tên phim và điểm đánh giá từ 0.5 đến 5 sao" : "the movie title and rating score (from 0.5 to 5 stars)");
            } else {
                plan.getToolCalls().add(new ChatAgentPlan.ToolCall("RATE_MOVIE",
                    jsonArguments(Map.of("query", movieQuery, "score", score))));
            }
            return plan;
        }

        switch (intent) {
            case MOVIE_SEARCH -> {
                plan.setIntent("MOVIE_SEARCH");
                plan.getToolCalls().add(new ChatAgentPlan.ToolCall("SEARCH_MOVIES",
                    jsonArguments(Map.of("query", movieQuery.isBlank() ? userMessage : movieQuery))));
            }
            case MOVIE_RECOMMENDATION -> plan.setIntent("MOVIE_RECOMMENDATION");
            case MOVIE_INFO -> {
                plan.setIntent("MOVIE_QA");
                if (!movieQuery.isBlank()) {
                    plan.getToolCalls().add(new ChatAgentPlan.ToolCall("GET_MOVIE_DETAIL",
                        jsonArguments(Map.of("query", movieQuery))));
                } else {
                    plan.setMissingInfo(isVi ? "tên phim bạn muốn hỏi" : "the movie title you want to ask about");
                }
            }
            case GREETING -> {
                plan.setIntent("SMALL_TALK");
                plan.setResponseGuidance(chatHelpService.getHelpResponse(intent, userMessage));
            }
            case ACCOUNT_HELP, WATCHLIST_HELP, RATING_HELP, HISTORY_HELP, SITE_NAVIGATION -> {
                plan.setIntent("WEBSITE_HELP");
                plan.setResponseGuidance(chatHelpService.getHelpResponse(intent, userMessage));
            }
            case OUT_OF_SCOPE -> {
                plan.setIntent("OUT_OF_SCOPE");
                plan.setResponseGuidance(chatHelpService.getHelpResponse(intent, userMessage));
            }
        }
        return plan;
    }

    private String extractMovieQuery(String message, List<Movie> candidates) {
        String normalizedMessage = removeAccent(message.toLowerCase(Locale.ROOT));
        for (Movie candidate : candidates) {
            if (candidate.getTitle() != null
                    && normalizedMessage.contains(removeAccent(candidate.getTitle().toLowerCase(Locale.ROOT)))) {
                return candidate.getTitle();
            }
        }

        String query = message.trim();
        query = query.replaceFirst("(?iu)^.*?\\bphim\\s+", "");
        query = query.replaceFirst("(?iu)\\s+(giúp tôi|giup toi|cho tôi|cho toi|nhé|nhe)[.!?]*$", "");
        query = query.replaceFirst("(?iu)\\s+(vào|vao)\\s+(watchlist|danh sách.*)$", "");
        query = query.replaceFirst("(?iu)\\s+[0-5](?:[.,]5)?\\s*(sao|star).*$", "");
        query = query.replaceFirst("(?iu)^(tìm|tim|kiếm|kiem|search)\\s+", "");
        return query.trim();
    }

    private Double extractRatingScore(String normalizedMessage) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("([0-5](?:[.,]5)?)\\s*(?:sao|star)")
            .matcher(normalizedMessage);
        if (!matcher.find()) return null;
        try {
            return Double.parseDouble(matcher.group(1).replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String jsonArguments(Map<String, Object> arguments) {
        try {
            return mapper.writeValueAsString(arguments);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private ChatResponse recommendMoviesLegacy(User user, String userMessage) {
        ChatIntent intent = intentClassifier.classify(userMessage);
        
        if (intent == ChatIntent.OUT_OF_SCOPE) {
            String reply = chatHelpService.getHelpResponse(ChatIntent.OUT_OF_SCOPE, userMessage);
            if (self != null) self.saveChatLogOnly(user, userMessage, reply);
            else saveChatLogOnly(user, userMessage, reply);
            return new ChatResponse("TEXT", reply, Collections.emptyList(), Collections.emptyList());
        }

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

        boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
        if (candidates.isEmpty() && !isEnabled()) {
            String reply = isVi 
                ? String.format("Rất tiếc, hiện tại hệ thống không tìm thấy bộ phim nào phù hợp với yêu cầu hoặc từ khóa '%s'. Bạn hãy thử tìm kiếm bằng tên phim hoặc thể loại khác nhé!", userMessage)
                : String.format("Sorry, currently the system could not find any movie matching the request or keyword '%s'. Please try searching with another title or genre!", userMessage);
            if (self != null) self.saveChatLogOnly(user, userMessage, reply);
            else saveChatLogOnly(user, userMessage, reply);
            return new ChatResponse("TEXT", reply, Collections.emptyList(), Collections.emptyList());
        }

        String likedMovies = isVi ? "Không có" : "None";
        String watchHistoryText = isVi ? "Không có" : "None";
        String favoriteGenres = isVi ? "Không có" : "None";

        if (user != null) {
            List<Rating> ratings = ratingRepository.findByUserUserId(user.getUserId());
            List<String> highRatings = ratings.stream()
                .filter(r -> r.getRating() >= 4.0 && r.getMovie() != null)
                .limit(5)
                .map(r -> r.getMovie().getTitle() + " (" + r.getRating() + (isVi ? " sao)" : " stars)"))
                .collect(Collectors.toList());
            if (!highRatings.isEmpty()) likedMovies = String.join(", ", highRatings);

            List<WatchHistory> history = watchHistoryRepository.findByUserUserIdOrderByWatchedAtDesc(user.getUserId());
            List<String> recentHistory = history.stream()
                .filter(wh -> wh.getMovie() != null)
                .limit(5)
                .map(wh -> wh.getMovie().getTitle())
                .collect(Collectors.toList());
            if (!recentHistory.isEmpty()) watchHistoryText = String.join(", ", recentHistory);

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

        StringBuilder candidatesBuilder = new StringBuilder();
        Map<Integer, Movie> candidateMap = new HashMap<>();
        for (Movie m : candidates) {
            candidateMap.put(m.getMovieId(), m);
            String genres = m.getGenres() != null ? m.getGenres().stream().map(Genre::getGenreName).collect(Collectors.joining(", ")) : "N/A";
            if (isVi) {
                candidatesBuilder.append(String.format("- ID: %d | Tên: %s | Năm: %s | Thể loại: [%s] | Rating: %.1f\n", 
                    m.getMovieId(), m.getTitle(), m.getReleaseYear() != null ? String.valueOf(m.getReleaseYear()) : "N/A", genres, m.getAverageRating()));
            } else {
                candidatesBuilder.append(String.format("- ID: %d | Title: %s | Year: %s | Genres: [%s] | Rating: %.1f\n", 
                    m.getMovieId(), m.getTitle(), m.getReleaseYear() != null ? String.valueOf(m.getReleaseYear()) : "N/A", genres, m.getAverageRating()));
            }
        }

        String systemPrompt = isVi ? String.format(
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
        ) : String.format(
            "You are a friendly and intelligent AI Movie Assistant for the MovieRecommendation (MovieRec) website.\n" +
            "Your task is to assist users in English with:\n" +
            "1. Movie recommendation & search (combining user preferences and available movies in the database).\n" +
            "2. Answering questions and guiding them on using MovieRec features (such as Account/Profile, Watchlist/Favorites, Rating/Review, Watch History, Website Navigation).\n" +
            "3. Natural conversation and friendly greetings.\n\n" +
            "MOVIEREC WEBSITE FEATURES GUIDE:\n" +
            "- Registration: Click 'Register' in the navigation bar or 'Create Free Account' on the homepage. Fill in username, email, password, and enter the OTP verification code sent to your email to complete activation.\n" +
            "- Login: Click 'Login' in the top right corner, enter your Email/Username and password.\n" +
            "- Forgot Password: On the Login page, click 'Forgot Password?'. Enter your registered email to receive an OTP code to restore and change your password.\n" +
            "- Change Password: Login -> Go to the 'Profile/Account' page in the navigation bar -> Select 'Change Password'.\n" +
            "- Edit Account Profile: Login -> Go to the 'Profile/Account' page in the navigation bar to update personal details.\n" +
            "- Watchlist: Save a movie by visiting its detail page and clicking '+ Watchlist' or 'Favorite'. Remove it by clicking that button again or by visiting the 'Watchlist' page from the navigation bar.\n" +
            "- Rate Movie (Rating/Review): Go to the movie detail page, scroll down to the 'Ratings & Reviews' section, choose your desired rating from 1 to 5 stars, and submit.\n" +
            "- Watch History: Go to 'Profile/Account' from the navigation bar -> select 'Watch History' to view details.\n" +
            "- Main Navigation: Homepage (new/popular movies), Search bar (search movies), Watchlist (saved movies), Profile (view history and edit account).\n\n" +
            "User preference information if available:\n" +
            "- Highly rated movies: %s\n" +
            "- Recently watched movies: %s\n" +
            "- Frequently watched genres: %s\n\n" +
            "List of candidate movies in the database:\n" +
            "%s\n\n" +
            "RESPONSE RULES (MUST RETURN VALID JSON ONLY, DO NOT CONTAIN EXTRA CHARACTERS OUTSIDE JSON SYNTAX):\n" +
            "{\n" +
            "  \"type\": \"TEXT\" or \"MOVIE_CARDS\",\n" +
            "  \"reply\": \"Detailed greeting, chat, or guide in English...\",\n" +
            "  \"movies\": [\n" +
            "     {\n" +
            "       \"movieId\": 123,\n" +
            "       \"reason\": \"Concise reason why you recommend this movie\"\n" +
            "     }\n" +
            "  ],\n" +
            "  \"actions\": [\n" +
            "     {\n" +
            "       \"name\": \"ADD_WATCHLIST\" or \"RATE_MOVIE\" or \"FILTER_MOVIES\" or \"VIEW_MOVIE_DETAIL\",\n" +
            "       \"params\": { ... }\n" +
            "     }\n" +
            "  ]\n" +
            "}\n\n" +
            "GUIDE FOR SELECTING TYPE AND MOVIES:\n" +
            "- If the user greets you, asks for account help, website guides, chats generally, or searches for a specific movie that is NOT in the candidate list above:\n" +
            "  1. Set type to \"TEXT\".\n" +
            "  2. Set movies to empty array [].\n" +
            "  3. In \"reply\", chat or guide naturally in English.\n" +
            "- If the user searches for movies or requests recommendations that match movies in the candidate list above:\n" +
            "  1. Set type to \"MOVIE_CARDS\".\n" +
            "  2. Choose up to 5 most suitable movies from the candidates list above and put them in the \"movies\" array. DO NOT FABRICATE MOVIES OR CHOOSE MOVIES OUTSIDE THE CANDIDATE LIST.\n" +
            "  3. Set \"reply\" as your greeting and summary of the reasons for recommending or search results.\n\n" +
            "GUIDE FOR ACTIONS (CRITICAL):\n" +
            "If the user requests a specific action, return it in the `actions` array to automate it for them:\n" +
            "1. ADD_WATCHLIST: Add a movie to their watchlist. Params: `{\"movieId\": integer}`. Example: 'save movie 123 to my watchlist', 'add Inception'.\n" +
            "2. RATE_MOVIE: Rate a movie. Params: `{\"movieId\": integer, \"score\": number (1.0 to 5.0)}`. Example: 'rate movie 123 5 stars', 'give Avatar 4 stars'.\n" +
            "3. VIEW_MOVIE_DETAIL: Open the movie details or play page. Params: `{\"movieId\": integer}`. Example: 'watch movie Interstellar', 'play Titanic'.\n" +
            "4. FILTER_MOVIES: Filter movie lists. Params: `{\"q\": string, \"genreId\": integer, \"year\": integer, \"minRating\": number, \"sortBy\": string}` (all optional). Example: 'filter action movies from 2020', 'find comedy movies with high ratings'.\n" +
            "If no action is requested, return an empty array `[]`.",
            likedMovies, watchHistoryText, favoriteGenres, candidatesBuilder.toString()
        );

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

        chatMessages.add(Map.of("role", "user", "content", userMessage));

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
                responseContent = null;
            }
        }

        if (responseContent == null || reply == null) {
            log.info("Using fallback recommendation for AI Chat");
            if (intent == ChatIntent.MOVIE_RECOMMENDATION || intent == ChatIntent.MOVIE_SEARCH) {
                if (!candidates.isEmpty()) {
                    responseType = "MOVIE_CARDS";
                    reply = isVi ? "Dựa trên yêu cầu của bạn, mình đã tìm thấy một số phim phù hợp từ hệ thống:" : "Based on your request, I found some matching movies from the system:";
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
                            isVi ? "Phim phổ biến trong hệ thống phù hợp với từ khóa của bạn." : "Popular movie in the system matching your keyword."
                        );
                        recommendedMovies.add(mDto);
                    }
                } else {
                    responseType = "TEXT";
                    reply = isVi 
                        ? String.format("Rất tiếc, hiện tại hệ thống không tìm thấy bộ phim nào phù hợp với yêu cầu hoặc từ khóa '%s'. Bạn hãy thử tìm kiếm bằng tên phim hoặc thể loại khác nhé!", userMessage)
                        : String.format("Sorry, currently the system could not find any movie matching the request or keyword '%s'. Please try searching with another title or genre!", userMessage);
                }
            } else {
                responseType = "TEXT";
                reply = chatHelpService.getHelpResponse(intent, userMessage);
            }
        }

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
        boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
        if (user == null) {
            return isVi 
                ? "Khách chưa đăng nhập; không được thực hiện thao tác thay đổi dữ liệu."
                : "Guest user is not logged in; data modification operations are not allowed.";
        }
        List<Rating> ratings = ratingRepository.findByUserUserId(user.getUserId());
        List<String> liked = ratings.stream().filter(rating -> rating.getRating() >= 4 && rating.getMovie() != null)
            .limit(5).map(rating -> rating.getMovie().getTitle() + " (" + rating.getRating() + (isVi ? " sao)" : " stars)")).toList();
        List<String> disliked = ratings.stream().filter(rating -> rating.getRating() <= 2 && rating.getMovie() != null)
            .limit(3).map(rating -> rating.getMovie().getTitle()).toList();
        List<String> history = watchHistoryRepository.findByUserUserIdOrderByWatchedAtDesc(user.getUserId()).stream()
            .filter(item -> item.getMovie() != null).limit(5).map(item -> item.getMovie().getTitle()).toList();
        String preferences;
        if (isVi) {
            preferences = userPreferenceRepository.findByUserUserId(user.getUserId())
                .map(value -> "thích=" + value.getPreferredGenres() + ", không thích=" + value.getDislikedGenres()
                    + ", rating tối thiểu=" + value.getMinRating())
                .orElse("chưa thiết lập");
            return "User đã đăng nhập (id=" + user.getUserId() + "). Phim thích: "
                + (liked.isEmpty() ? "chưa có" : String.join(", ", liked))
                + ". Phim không thích: " + (disliked.isEmpty() ? "chưa có" : String.join(", ", disliked))
                + ". Xem gần đây: " + (history.isEmpty() ? "chưa có" : String.join(", ", history))
                + ". Preferences: " + preferences + ".";
        } else {
            preferences = userPreferenceRepository.findByUserUserId(user.getUserId())
                .map(value -> "liked=" + value.getPreferredGenres() + ", disliked=" + value.getDislikedGenres()
                    + ", min rating=" + value.getMinRating())
                .orElse("not set");
            return "User is logged in (id=" + user.getUserId() + "). Liked movies: "
                + (liked.isEmpty() ? "none" : String.join(", ", liked))
                + ". Disliked movies: " + (disliked.isEmpty() ? "none" : String.join(", ", disliked))
                + ". Recently watched: " + (history.isEmpty() ? "none" : String.join(", ", history))
                + ". Preferences: " + preferences + ".";
        }
    }

    private String buildAgentCandidateCatalog(List<Movie> candidates) {
        boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
        if (candidates == null || candidates.isEmpty()) return isVi ? "Không có phim phù hợp trong database." : "No suitable movies found in database.";
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

        List<Movie> textSearch = movieRepository.searchByTitleOrGenre(message);
        if (textSearch != null) {
            matchedMovies.addAll(textSearch);
        }

        if (isEnabled() && movieEmbeddingService != null) {
            List<Movie> semanticSearch = movieEmbeddingService.searchSemantic(message, 30);
            if (semanticSearch != null) {
                matchedMovies.addAll(semanticSearch);
            }
        }

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
            if (intent == ChatIntent.OUT_OF_SCOPE || intent == ChatIntent.GREETING) {
                return chatHelpService.getHelpResponse(intent, userMessage);
            }
        }

        String msgLowerRaw = userMessage.toLowerCase().trim();
        boolean isSummaryRequest = msgLowerRaw.contains("tóm tắt") || msgLowerRaw.contains("tom tat") 
                || msgLowerRaw.contains("summary") || msgLowerRaw.contains("timeline");

        String title = movie.getTitle();
        boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
        String genres = movie.getGenres() != null ? movie.getGenres().stream().map(Genre::getGenreName).collect(Collectors.joining(", ")) : (isVi ? "Chưa rõ" : "Unknown");

        if (isEnabled()) {
            try {
                String systemPrompt;
                if (isVi) {
                    systemPrompt = String.format(
                        "Bạn là trợ lý AI thân thiện cho trang web MovieRecommendation.\n" +
                        "Bạn đang cùng người dùng xem bộ phim '%s' (Năm: %d, Thể loại: %s, Mô tả: %s) trên trình phát video của bên thứ ba (Server 1).\n" +
                        "Người dùng gửi tin nhắn: '%s'\n\n" +
                        "YÊU CẦU:\n" +
                        "1. Trả lời câu hỏi của người dùng bằng tiếng Việt, thân thiện, tự nhiên.\n" +
                        "2. Nếu người dùng yêu cầu tóm tắt phim/video hoặc chia timeline:\n" +
                        "   - Hãy tự phân tích, ước lượng và phân chia dòng thời gian (timeline) của bộ phim thành khoảng 3-5 mốc thời gian logic của nội dung phim (ví dụ mốc mở đầu, giới thiệu nhân vật, diễn biến chính, cao trào kịch tính, kết thúc) theo dạng `[MM:SS] - Tên sự kiện/phân cảnh`.\n" +
                        "   - Các mốc này phải dựa trên mô tả cốt truyện phim được cung cấp. Tuyệt đối không để mốc thời gian trống.\n" +
                        "   - Đảm bảo các mốc thời gian có dạng `[MM:SS]` (ví dụ `[05:20]`, `[85:40]`). KHÔNG sử dụng định dạng giờ `HH:MM:SS` (ví dụ `[01:25:40]`) vì code xử lý của web chỉ hỗ trợ click tua theo định dạng `[phút:giây]` với số phút có thể lớn hơn 59.\n" +
                        "3. Nếu họ chào hỏi hoặc hỏi về tính năng website, hãy trả lời và hướng dẫn họ một cách thân thiện (ví dụ cách đăng ký/đăng nhập, cách đổi mật khẩu, cách đánh giá phim, cách quản lý Watchlist...).\n" +
                        "4. KHÔNG sử dụng các định dạng markdown phức tạp khác ngoại trừ danh sách và in đậm. Đảm bảo các mốc thời gian đều nằm trong cặp ngoặc vuông `[MM:SS]` để người dùng click tua được phim.",
                        movie.getTitle(),
                        movie.getReleaseYear() != null ? movie.getReleaseYear() : 2026,
                        genres,
                        movie.getDescription() != null ? movie.getDescription() : "Không có mô tả.",
                        userMessage
                    );
                } else {
                    systemPrompt = String.format(
                        "You are a friendly AI assistant for the MovieRecommendation website.\n" +
                        "You are watching the movie '%s' (Year: %d, Genre: %s, Description: %s) with the user on a third-party video player (Server 1).\n" +
                        "The user sent the message: '%s'\n\n" +
                        "REQUIREMENTS:\n" +
                        "1. Respond to the user's questions in English, friendly and naturally.\n" +
                        "2. If the user asks for a movie/video summary or timeline splitting:\n" +
                        "   - Analyze, estimate, and partition the timeline of the movie into about 3-5 logical milestones based on the movie content (e.g., introduction, introducing characters, main plot, climax, ending) in the format `[MM:SS] - Event/scene name`.\n" +
                        "   - These milestones must be based on the provided movie plot description. Never leave time milestones blank.\n" +
                        "   - Ensure that the timestamps are in `[MM:SS]` format (e.g., `[05:20]`, `[85:40]`). DO NOT use hour format `HH:MM:SS` (e.g., `[01:25:40]`) because the web's player handler only supports clicking to seek using the `[minutes:seconds]` format where minutes can be greater than 59.\n" +
                        "3. If they greet you or ask about website features, reply and guide them in a friendly manner (e.g., how to register/login, how to change passwords, how to rate movies, how to manage Watchlist...).\n" +
                        "4. DO NOT use other complex markdown formatting except lists and bold. Ensure all timestamps are enclosed in square brackets `[MM:SS]` so that users can click to seek the movie.",
                        movie.getTitle(),
                        movie.getReleaseYear() != null ? movie.getReleaseYear() : 2026,
                        genres,
                        movie.getDescription() != null ? movie.getDescription() : "No description available.",
                        userMessage
                    );
                }

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

        // Fallback or Rule-based response when OpenAI is disabled or fails
        if (isSummaryRequest || msgLower.contains("phân cảnh") || msgLower.contains("phan canh")
                || msgLower.contains("mốc thời gian") || msgLower.contains("moc thoi gian")
                || msgLower.contains("chien dau") || msgLower.contains("fight")
                || msgLower.contains("hành động") || msgLower.contains("hanh dong")) {
            
            // Generate a dynamic estimated timeline based on movie description
            StringBuilder sb = new StringBuilder();
            if (isVi) {
                sb.append(String.format("Dòng thời gian tóm tắt (ước lượng) cho video/trailer phim **%s**:\n\n", title));
                sb.append("- **[00:10]**: Giới thiệu bối cảnh ban đầu và các nhân vật chính.\n");
                sb.append("- **[00:45]**: Phát sinh mâu thuẫn chính hoặc khởi đầu hành trình.\n");
                sb.append("- **[01:15]**: Phân cảnh hành động kịch tính và cao trào nổi bật.\n");
                sb.append("- **[02:00]**: Đoạn kết trailer với những hình ảnh ấn tượng và thông tin phát hành.\n\n");
                sb.append("Bạn có thể nhấn vào các mốc thời gian `[MM:SS]` ở trên để tua nhanh đến phân đoạn đó!");
            } else {
                sb.append(String.format("Estimated summary timeline for the video/trailer of **%s**:\n\n", title));
                sb.append("- **[00:10]**: Introduction to the initial setting and main characters.\n");
                sb.append("- **[00:45]**: Main conflict arises or the journey begins.\n");
                sb.append("- **[01:15]**: Dramatic action sequence and prominent climax.\n");
                sb.append("- **[02:00]**: Trailer conclusion with striking visuals and release info.\n\n");
                sb.append("You can click on the `[MM:SS]` timestamps above to quickly seek to that segment!");
            }
            return sb.toString();
        }
        
        // 2. Check for description / overview / content
        if (msgLower.contains("mô tả") || msgLower.contains("mo ta") 
                || msgLower.contains("nội dung") || msgLower.contains("noi dung")
                || msgLower.contains("cốt truyện") || msgLower.contains("cot truyen")
                || msgLower.contains("about") || msgLower.contains("description")) {
            return isVi 
                ? String.format("Bộ phim **%s** có nội dung tóm tắt như sau:\n\n%s", title, movie.getDescription() != null ? movie.getDescription() : "Không có mô tả chi tiết.")
                : String.format("The movie **%s** has the following summary:\n\n%s", title, movie.getDescription() != null ? movie.getDescription() : "No detailed description available.");
        }
        
        // 3. Check for release year
        if (msgLower.contains("năm") || msgLower.contains("nam") 
                || msgLower.contains("sản xuất") || msgLower.contains("san xuat")
                || msgLower.contains("chiếu") || msgLower.contains("chieu")
                || msgLower.contains("year") || msgLower.contains("release")) {
            return isVi
                ? String.format("Phim **%s** được phát hành vào năm **%s**.", title, movie.getReleaseYear() != null ? String.valueOf(movie.getReleaseYear()) : "Chưa rõ")
                : String.format("The movie **%s** was released in the year **%s**.", title, movie.getReleaseYear() != null ? String.valueOf(movie.getReleaseYear()) : "Unknown");
        }
        
        // 4. Check for rating
        if (msgLower.contains("đánh giá") || msgLower.contains("danh gia") 
                || msgLower.contains("điểm") || msgLower.contains("diem")
                || msgLower.contains("rating") || msgLower.contains("sao")) {
            return isVi
                ? String.format("Phim **%s** hiện có điểm đánh giá trung bình là **%.1f/5** sao trên hệ thống.", title, movie.getAverageRating())
                : String.format("The movie **%s** currently has an average rating of **%.1f/5** stars on the system.", title, movie.getAverageRating());
        }

        // 5. Check for genres
        if (msgLower.contains("thể loại") || msgLower.contains("the loai") || msgLower.contains("genre")) {
            return isVi
                ? String.format("Phim **%s** thuộc các thể loại: *%s*.", title, genres)
                : String.format("The movie **%s** belongs to the following genres: *%s*.", title, genres);
        }

        // 6. Check for similar movies
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
                if (isVi) {
                    sb.append(String.format("Dưới đây là một số phim tương tự cùng thể loại với **%s** mà bạn có thể quan tâm:\n\n", title));
                    for (Movie m : similar) {
                        sb.append(String.format("- **%s** (%s) - Rating: %.1f\n", m.getTitle(), m.getReleaseYear() != null ? String.valueOf(m.getReleaseYear()) : "Chưa rõ", m.getAverageRating()));
                    }
                    sb.append("\nBạn có thể nhấn vào các bộ phim này trên trang danh sách hoặc tìm kiếm chúng để xem thêm!");
                } else {
                    sb.append(String.format("Here are some similar movies in the same genres as **%s** that you might be interested in:\n\n", title));
                    for (Movie m : similar) {
                        sb.append(String.format("- **%s** (%s) - Rating: %.1f\n", m.getTitle(), m.getReleaseYear() != null ? String.valueOf(m.getReleaseYear()) : "Unknown", m.getAverageRating()));
                    }
                    sb.append("\nYou can click on these movies on the movie list page or search for them to watch more!");
                }
                return sb.toString();
            } else {
                return isVi
                    ? String.format("Hiện tại hệ thống chưa tìm thấy bộ phim nào tương tự với **%s**. Bạn hãy thử xem thêm các bộ phim khác cùng thể loại *%s* nhé!", title, genres)
                    : String.format("Currently, the system could not find any similar movies for **%s**. Please try viewing other movies in the *%s* genres!", title, genres);
            }
        }

        // Default movie QA fallback
        if (isVi) {
            return String.format(
                "Chào bạn! Bộ phim **%s** thuộc thể loại *%s*. %s\n\n" +
                "Hệ thống chưa có timeline/transcript chính xác, nên mình sẽ không tự tạo các mốc thời gian. " +
                "Bạn có thể hỏi mình về mô tả, năm phát hành, thể loại hoặc rating có trong database.",
                title, genres,
                movie.getDescription() == null ? "Thông tin mô tả chưa được cập nhật." : movie.getDescription()
            );
        } else {
            return String.format(
                "Hello! The movie **%s** belongs to the *%s* genre. %s\n\n" +
                "The system does not have an exact timeline/transcript, so I will not generate custom timestamps. " +
                "You can ask me about the description, release year, genres, or rating in the database.",
                title, genres,
                movie.getDescription() == null ? "Description info has not been updated yet." : movie.getDescription()
            );
        }
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
