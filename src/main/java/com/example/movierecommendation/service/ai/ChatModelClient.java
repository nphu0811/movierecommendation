package com.example.movierecommendation.service.ai;

import java.util.List;
import java.util.Map;

/**
 * Provider-neutral contract for OpenAI-compatible chat models.
 */
public interface ChatModelClient {
    boolean isEnabled();

    String complete(
        List<Map<String, Object>> messages,
        Map<String, Object> responseFormat,
        int maxTokens,
        double temperature
    );
}
