package com.example.movierecommendation.service.ai;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatAgentResponder {
    private final ChatModelClient modelClient;

    public ChatAgentResponder(ChatModelClient modelClient) {
        this.modelClient = modelClient;
    }

    public String respond(String userMessage, String userContext, ChatAgentPlan plan, List<ChatToolResult> results) {
        if (plan != null && plan.getMissingInfo() != null && !plan.getMissingInfo().isBlank()) {
            return "Mình cần thêm thông tin: " + plan.getMissingInfo();
        }
        ChatToolResult failedMutation = results == null ? null : results.stream()
            .filter(result -> ("ADD_WATCHLIST".equals(result.getTool()) || "RATE_MOVIE".equals(result.getTool()))
                && !result.isSuccess())
            .findFirst().orElse(null);
        if (failedMutation != null) return failedMutation.getMessage();

        if (!modelClient.isEnabled()) return fallback(plan, results);

        String verifiedResults = results == null || results.isEmpty()
            ? "Không có tool nào được gọi."
            : results.stream().map(result -> String.format("- %s | success=%s | %s",
                result.getTool(), result.isSuccess(), result.getMessage())).collect(Collectors.joining("\n"));

        String system = """
            Bạn là MovieRec AI Assistant. Hãy trả lời tự nhiên bằng tiếng Việt, ngắn gọn và hữu ích.
            Chỉ dùng dữ liệu trong VERIFIED TOOL RESULTS và USER CONTEXT.
            Không bịa phim, rating, diễn viên, đạo diễn hoặc timestamp.
            Chỉ nói đã lưu/chấm điểm khi đúng tool có success=true.
            Nếu tool thất bại hoặc không có dữ liệu, nói rõ giới hạn đó. Không trả JSON và không dùng markdown phức tạp.
            """;
        String evidence = "INTENT: " + plan.getIntent()
            + "\nMISSING INFO: " + plan.getMissingInfo()
            + "\nGUIDANCE: " + plan.getResponseGuidance()
            + "\nUSER CONTEXT: " + userContext
            + "\nVERIFIED TOOL RESULTS:\n" + verifiedResults;

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        messages.add(Map.of("role", "system", "content", evidence));
        messages.add(Map.of("role", "user", "content", userMessage));
        String response = modelClient.complete(messages, null, 450, 0.4);
        return response == null || response.isBlank() ? fallback(plan, results) : response;
    }

    private String fallback(ChatAgentPlan plan, List<ChatToolResult> results) {
        if (plan != null && plan.getMissingInfo() != null && !plan.getMissingInfo().isBlank()) {
            return "Mình cần thêm thông tin: " + plan.getMissingInfo();
        }
        if (results != null && !results.isEmpty()) {
            return results.stream().map(ChatToolResult::getMessage).collect(Collectors.joining(" "));
        }
        if (plan != null && plan.getResponseGuidance() != null && !plan.getResponseGuidance().isBlank()) {
            return plan.getResponseGuidance();
        }
        return "Mình có thể giúp bạn tìm phim, gợi ý theo sở thích và hướng dẫn sử dụng MovieRec.";
    }
}
