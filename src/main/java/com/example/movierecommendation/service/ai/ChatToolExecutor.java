package com.example.movierecommendation.service.ai;

import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.entity.UserPreference;
import com.example.movierecommendation.repository.MovieRepository;
import com.example.movierecommendation.repository.UserPreferenceRepository;
import com.example.movierecommendation.service.InteractionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ChatToolExecutor {
    private static final ObjectMapper mapper = new ObjectMapper();

    private final MovieRepository movieRepository;
    private final InteractionService interactionService;
    private final UserPreferenceRepository userPreferenceRepository;

    public ChatToolExecutor(MovieRepository movieRepository, InteractionService interactionService,
                            UserPreferenceRepository userPreferenceRepository) {
        this.movieRepository = movieRepository;
        this.interactionService = interactionService;
        this.userPreferenceRepository = userPreferenceRepository;
    }

    public List<ChatToolResult> execute(User user, ChatAgentPlan plan, List<Movie> candidates) {
        if (plan == null || plan.getToolCalls() == null) return Collections.emptyList();
        Map<Integer, Movie> allowedMovies = candidates.stream()
            .filter(movie -> movie.getMovieId() != null && movie.getDeletedAt() == null)
            .collect(Collectors.toMap(Movie::getMovieId, movie -> movie, (first, ignored) -> first, LinkedHashMap::new));

        List<ChatToolResult> results = new ArrayList<>();
        for (ChatAgentPlan.ToolCall call : plan.getToolCalls().stream().limit(6).toList()) {
            results.add(executeOne(user, call, allowedMovies));
        }
        return results;
    }

    private ChatToolResult executeOne(User user, ChatAgentPlan.ToolCall call, Map<Integer, Movie> allowedMovies) {
        String name = call == null || call.getName() == null ? "UNKNOWN" : call.getName();
        JsonNode args = parseArguments(call == null ? null : call.getArguments());
        try {
            return switch (name) {
                case "SEARCH_MOVIES" -> searchMovies(args, allowedMovies);
                case "RECOMMEND_MOVIES" -> movieSelection(name, args, allowedMovies);
                case "GET_MOVIE_DETAIL" -> movieDetail(args, allowedMovies);
                case "ADD_WATCHLIST" -> addWatchlist(user, args, allowedMovies);
                case "RATE_MOVIE" -> rateMovie(user, args, allowedMovies);
                case "GET_USER_PREFERENCES" -> userPreferences(user);
                case "VIEW_MOVIE_DETAIL" -> viewMovieDetail(args, allowedMovies);
                case "FILTER_MOVIES" -> filterMovies(args);
                default -> failure(name, "Tool không được hỗ trợ.");
            };
        } catch (Exception e) {
            return failure(name, "Không thể thực thi tool: " + safeMessage(e));
        }
    }

    private ChatToolResult movieSelection(String name, JsonNode args, Map<Integer, Movie> allowedMovies) {
        List<Movie> selected = new ArrayList<>();
        JsonNode ids = args.path("movieIds");
        if (ids.isArray()) {
            for (JsonNode id : ids) {
                Movie movie = allowedMovies.get(id.asInt());
                if (movie != null && selected.stream().noneMatch(item -> item.getMovieId().equals(movie.getMovieId()))) {
                    selected.add(movie);
                }
                if (selected.size() == 5) break;
            }
        }
        if (selected.isEmpty()) selected.addAll(allowedMovies.values().stream().limit(5).toList());
        if (selected.isEmpty()) return failure(name, "Không tìm thấy phim phù hợp trong cơ sở dữ liệu.");
        return new ChatToolResult(name, true, "Đã lấy " + selected.size() + " phim từ cơ sở dữ liệu.", selected, null);
    }

    private ChatToolResult searchMovies(JsonNode args, Map<Integer, Movie> allowedMovies) {
        String query = args.path("query").asText("").trim();
        if (!query.isEmpty()) {
            List<Movie> found = movieRepository.searchByTitleOrGenre(query);
            if (found != null) {
                List<Movie> valid = found.stream().filter(movie -> movie.getDeletedAt() == null).distinct().limit(5).toList();
                if (!valid.isEmpty()) {
                    return new ChatToolResult("SEARCH_MOVIES", true,
                        "Đã tìm thấy " + valid.size() + " phim trong cơ sở dữ liệu.", valid, null);
                }
            }
        }
        return movieSelection("SEARCH_MOVIES", args, allowedMovies);
    }

    private ChatToolResult movieDetail(JsonNode args, Map<Integer, Movie> allowedMovies) {
        Movie movie = resolveMovie(args, allowedMovies).orElse(null);
        if (movie == null) return failure("GET_MOVIE_DETAIL", "Không tìm thấy phim hợp lệ trong cơ sở dữ liệu.");
        String genres = movie.getGenres() == null ? "chưa cập nhật" : movie.getGenres().stream()
            .map(genre -> genre.getGenreName()).collect(Collectors.joining(", "));
        String message = String.format("%s (%s), thể loại %s, rating %.1f/5. %s",
            movie.getTitle(), movie.getReleaseYear() == null ? "chưa rõ năm" : movie.getReleaseYear(),
            genres, movie.getAverageRating(), movie.getDescription() == null ? "" : movie.getDescription());
        return new ChatToolResult("GET_MOVIE_DETAIL", true, message, List.of(movie), null);
    }

    private ChatToolResult addWatchlist(User user, JsonNode args, Map<Integer, Movie> allowedMovies) {
        if (user == null) return failure("ADD_WATCHLIST", "Bạn cần đăng nhập để thêm phim vào Watchlist.");
        Movie movie = resolveMovie(args, allowedMovies).orElse(null);
        if (movie == null) return failure("ADD_WATCHLIST", "Không tìm thấy phim hợp lệ để thêm vào Watchlist.");
        boolean added = interactionService.addToWatchlist(user.getUserId(), movie.getMovieId());
        String message = added
            ? "Đã thêm " + movie.getTitle() + " vào Watchlist thành công."
            : movie.getTitle() + " đã có sẵn trong Watchlist.";
        return new ChatToolResult("ADD_WATCHLIST", true, message, List.of(movie), null);
    }

    private ChatToolResult rateMovie(User user, JsonNode args, Map<Integer, Movie> allowedMovies) {
        if (user == null) return failure("RATE_MOVIE", "Bạn cần đăng nhập để đánh giá phim.");
        Movie movie = resolveMovie(args, allowedMovies).orElse(null);
        if (movie == null) return failure("RATE_MOVIE", "Không tìm thấy phim hợp lệ để đánh giá.");
        double score = args.path("score").asDouble(-1);
        if (score < 0.5 || score > 5.0 || Math.round(score * 2) != score * 2) {
            return failure("RATE_MOVIE", "Điểm đánh giá phải từ 0.5 đến 5.0 theo bước nửa sao.");
        }
        interactionService.rateMovie(user.getUserId(), movie.getMovieId(), score);
        return new ChatToolResult("RATE_MOVIE", true,
            String.format("Đã đánh giá %s %.1f sao thành công.", movie.getTitle(), score), List.of(movie), null);
    }

    private ChatToolResult userPreferences(User user) {
        if (user == null) return failure("GET_USER_PREFERENCES", "Chưa có người dùng đăng nhập để cá nhân hóa.");
        Optional<UserPreference> preference = userPreferenceRepository.findByUserUserId(user.getUserId());
        if (preference.isEmpty()) return failure("GET_USER_PREFERENCES", "Người dùng chưa thiết lập sở thích phim.");
        UserPreference value = preference.get();
        String message = String.format("Thể loại thích: %s; không thích: %s; rating tối thiểu: %s.",
            textOrNone(value.getPreferredGenres()), textOrNone(value.getDislikedGenres()),
            value.getMinRating() == null ? "chưa đặt" : value.getMinRating());
        return new ChatToolResult("GET_USER_PREFERENCES", true, message, Collections.emptyList(), null);
    }

    private ChatToolResult viewMovieDetail(JsonNode args, Map<Integer, Movie> allowedMovies) {
        Movie movie = resolveMovie(args, allowedMovies).orElse(null);
        if (movie == null) return failure("VIEW_MOVIE_DETAIL", "Không tìm thấy phim hợp lệ để mở.");
        Map<String, Object> action = Map.of("name", "VIEW_MOVIE_DETAIL", "params", Map.of("movieId", movie.getMovieId()));
        return new ChatToolResult("VIEW_MOVIE_DETAIL", true, "Sẵn sàng mở trang chi tiết " + movie.getTitle() + ".",
            List.of(movie), action);
    }

    private ChatToolResult filterMovies(JsonNode args) {
        Map<String, Object> params = new LinkedHashMap<>();
        copyText(args, params, "q");
        copyInt(args, params, "year");
        copyDouble(args, params, "minRating");
        copyText(args, params, "sortBy");
        Map<String, Object> action = Map.of("name", "FILTER_MOVIES", "params", params);
        return new ChatToolResult("FILTER_MOVIES", true, "Đã chuẩn bị bộ lọc phim theo yêu cầu.",
            Collections.emptyList(), action);
    }

    private Optional<Movie> allowedMovie(int movieId, Map<Integer, Movie> allowedMovies) {
        Movie movie = allowedMovies.get(movieId);
        if (movie != null) return Optional.of(movie);
        return movieRepository.findById(movieId).filter(item -> item.getDeletedAt() == null && allowedMovies.containsKey(movieId));
    }

    private Optional<Movie> resolveMovie(JsonNode args, Map<Integer, Movie> allowedMovies) {
        Optional<Movie> byId = allowedMovie(args.path("movieId").asInt(-1), allowedMovies);
        if (byId.isPresent()) return byId;
        String query = args.path("query").asText("").trim();
        if (query.isEmpty()) return Optional.empty();
        List<Movie> matches = movieRepository.searchByTitleOrGenre(query);
        if (matches == null) return Optional.empty();
        return matches.stream()
            .filter(movie -> movie.getDeletedAt() == null)
            .sorted((first, second) -> {
                boolean firstExact = first.getTitle() != null && first.getTitle().equalsIgnoreCase(query);
                boolean secondExact = second.getTitle() != null && second.getTitle().equalsIgnoreCase(query);
                return Boolean.compare(secondExact, firstExact);
            })
            .findFirst();
    }

    private JsonNode parseArguments(String raw) {
        try {
            return mapper.readTree(raw == null || raw.isBlank() ? "{}" : raw);
        } catch (Exception ignored) {
            return mapper.createObjectNode();
        }
    }

    private ChatToolResult failure(String tool, String message) {
        return new ChatToolResult(tool, false, message, Collections.emptyList(), null);
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank() ? "lỗi dữ liệu" : e.getMessage();
    }

    private String textOrNone(String value) { return value == null || value.isBlank() ? "chưa đặt" : value; }
    private void copyText(JsonNode source, Map<String, Object> target, String key) {
        if (source.hasNonNull(key) && !source.path(key).asText().isBlank()) target.put(key, source.path(key).asText());
    }
    private void copyInt(JsonNode source, Map<String, Object> target, String key) {
        if (source.hasNonNull(key) && source.path(key).canConvertToInt()) target.put(key, source.path(key).asInt());
    }
    private void copyDouble(JsonNode source, Map<String, Object> target, String key) {
        if (source.hasNonNull(key) && source.path(key).isNumber()) target.put(key, source.path(key).asDouble());
    }
}
