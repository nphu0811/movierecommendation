package com.example.movierecommendation.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ChatAgentPlanner {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final ChatModelClient modelClient;

    public ChatAgentPlanner(ChatModelClient modelClient) {
        this.modelClient = modelClient;
    }

    public boolean isEnabled() {
        return modelClient.isEnabled();
    }

    public Optional<ChatAgentPlan> plan(String userMessage, String userContext, String candidateCatalog,
                                        List<Map<String, Object>> recentConversation) {
        if (!isEnabled()) return Optional.empty();

        boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
        String systemPrompt;
        if (isVi) {
            systemPrompt = """
                Bạn là planner của MovieRec AI Assistant. Hãy hiểu ý định tự nhiên, chọn tool cần gọi, nhưng không tự thực thi và không tự bịa dữ liệu.

                Intent hợp lệ: MOVIE_SEARCH, MOVIE_RECOMMENDATION, MOVIE_QA, VIDEO_QA, WEBSITE_HELP, USER_ACTION, SMALL_TALK, OUT_OF_SCOPE.

                Tools:
                - SEARCH_MOVIES: tìm phim trong database; arguments JSON: {\"query\":\"Inception\",\"movieIds\":[1,2]}
                - RECOMMEND_MOVIES: chọn tối đa 5 phim phù hợp; arguments JSON: {\"movieIds\":[1,2]}
                - GET_MOVIE_DETAIL: lấy dữ liệu một phim; arguments JSON: {\"movieId\":1} hoặc {\"query\":\"Inception\"}
                - ADD_WATCHLIST: lưu phim; arguments JSON: {\"movieId\":1} hoặc {\"query\":\"Inception\"}
                - RATE_MOVIE: chấm điểm; arguments JSON: {\"movieId\":1,\"score\":4.5} hoặc {\"query\":\"Inception\",\"score\":4.5}
                - GET_VIDEO_TIMELINE: lấy timeline thật; arguments JSON: {\"movieId\":1}
                - GET_USER_PREFERENCES: lấy gu người dùng; arguments JSON: {}
                - VIEW_MOVIE_DETAIL: mở trang chi tiết; arguments JSON: {\"movieId\":1}
                - FILTER_MOVIES: mở bộ lọc; arguments JSON: {\"q\":\"...\",\"year\":2020,\"minRating\":4.0,\"sortBy\":\"rating\"}

                Quy tắc bắt buộc:
                - Khi đã biết movieId, chỉ dùng ID có trong catalog. Khi phim chưa có trong catalog, truyền query tên phim để backend tìm trong database.
                - Không được khẳng định đã lưu/chấm điểm khi tool chưa trả success.
                - Không được bịa phim, rating, diễn viên, đạo diễn hoặc timestamp.
                - Nếu thiếu movieId/điểm số cần thiết, ghi rõ missingInfo và không gọi tool thay đổi dữ liệu.
                - Với câu hỏi ngoài phim và cách dùng MovieRec, chọn OUT_OF_SCOPE.
                - arguments phải là chuỗi JSON object hợp lệ. Không bọc markdown.
                """;
        } else {
            systemPrompt = """
                You are the planner for MovieRec AI Assistant. Understand the user's natural language intent, select the appropriate tools to call, but do not execute them yourself and do not make up data.

                Valid Intents: MOVIE_SEARCH, MOVIE_RECOMMENDATION, MOVIE_QA, VIDEO_QA, WEBSITE_HELP, USER_ACTION, SMALL_TALK, OUT_OF_SCOPE.

                Tools:
                - SEARCH_MOVIES: search for movies in the database; arguments JSON: {\"query\":\"Inception\",\"movieIds\":[1,2]}
                - RECOMMEND_MOVIES: select up to 5 matching movies; arguments JSON: {\"movieIds\":[1,2]}
                - GET_MOVIE_DETAIL: retrieve data for a single movie; arguments JSON: {\"movieId\":1} or {\"query\":\"Inception\"}
                - ADD_WATCHLIST: save movie to watchlist; arguments JSON: {\"movieId\":1} or {\"query\":\"Inception\"}
                - RATE_MOVIE: rate a movie; arguments JSON: {\"movieId\":1,\"score\":4.5} or {\"query\":\"Inception\",\"score\":4.5}
                - GET_VIDEO_TIMELINE: retrieve actual video timeline; arguments JSON: {\"movieId\":1}
                - GET_USER_PREFERENCES: retrieve user preferences; arguments JSON: {}
                - VIEW_MOVIE_DETAIL: open movie detail page; arguments JSON: {\"movieId\":1}
                - FILTER_MOVIES: open search filters; arguments JSON: {\"q\":\"...\",\"year\":2020,\"minRating\":4.0,\"sortBy\":\"rating\"}

                Mandatory Rules:
                - When movieId is known, only use IDs present in the catalog. When the movie is not in the catalog, pass query (movie title) for the backend to search.
                - Do not assert that a movie is saved/rated until the tool returns success=true.
                - Do not make up movies, ratings, actors, directors, or timestamps.
                - If a required movieId/rating score is missing, explicitly fill missingInfo and do not call mutating tools.
                - For questions unrelated to movies or MovieRec features, select OUT_OF_SCOPE.
                - arguments must be a valid JSON object string. Do not wrap in markdown block.
                """;
        }

        String context = "USER CONTEXT:\n" + userContext + "\n\nDATABASE CATALOG:\n" + candidateCatalog;
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "system", "content", context));
        if (recentConversation != null) messages.addAll(recentConversation);
        messages.add(Map.of("role", "user", "content", userMessage));

        String content = modelClient.complete(messages, planResponseFormat(), 650, 0.1);
        if (content == null) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(content, ChatAgentPlan.class));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Map<String, Object> planResponseFormat() {
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("type", "object");
        toolCall.put("properties", Map.of(
            "name", Map.of("type", "string", "enum", List.of(
                "SEARCH_MOVIES", "RECOMMEND_MOVIES", "GET_MOVIE_DETAIL", "ADD_WATCHLIST",
                "RATE_MOVIE", "GET_VIDEO_TIMELINE", "GET_USER_PREFERENCES",
                "VIEW_MOVIE_DETAIL", "FILTER_MOVIES"
            )),
            "arguments", Map.of("type", "string")
        ));
        toolCall.put("required", List.of("name", "arguments"));
        toolCall.put("additionalProperties", false);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
            "intent", Map.of("type", "string", "enum", List.of(
                "MOVIE_SEARCH", "MOVIE_RECOMMENDATION", "MOVIE_QA", "VIDEO_QA",
                "WEBSITE_HELP", "USER_ACTION", "SMALL_TALK", "OUT_OF_SCOPE"
            )),
            "confidence", Map.of("type", "number"),
            "missingInfo", Map.of("type", "string"),
            "responseGuidance", Map.of("type", "string"),
            "toolCalls", Map.of("type", "array", "items", toolCall)
        ));
        schema.put("required", List.of("intent", "confidence", "missingInfo", "responseGuidance", "toolCalls"));
        schema.put("additionalProperties", false);

        return Map.of("type", "json_schema", "json_schema", Map.of(
            "name", "movie_agent_plan",
            "strict", true,
            "schema", schema
        ));
    }
}
