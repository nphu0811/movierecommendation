package com.example.movierecommendation.service.ai;

import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.repository.MovieRepository;
import com.example.movierecommendation.repository.UserPreferenceRepository;
import com.example.movierecommendation.service.AIChatService;
import com.example.movierecommendation.service.ChatHelpService;
import com.example.movierecommendation.service.ChatIntentClassifier;
import com.example.movierecommendation.service.InteractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatAgentOrchestrationTest {
    private MovieRepository movieRepository;
    private InteractionService interactionService;
    private UserPreferenceRepository userPreferenceRepository;
    private ChatToolExecutor executor;
    private Movie movie;

    @BeforeEach
    void setUp() {
        movieRepository = mock(MovieRepository.class);
        interactionService = mock(InteractionService.class);
        userPreferenceRepository = mock(UserPreferenceRepository.class);
        executor = new ChatToolExecutor(movieRepository, interactionService,
            userPreferenceRepository);

        movie = new Movie();
        movie.setMovieId(42);
        movie.setTitle("Interstellar");
        movie.setAverageRating(4.7);
    }

    @Test
    void guestCannotMutateWatchlist() {
        ChatAgentPlan plan = plan("ADD_WATCHLIST", "{\"movieId\":42}");

        ChatToolResult result = executor.execute(null, plan, List.of(movie)).getFirst();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("đăng nhập"));
        verifyNoInteractions(interactionService);
    }

    @Test
    void watchlistToolExecutesOnBackendAndReturnsNoClientMutation() {
        User user = new User();
        user.setUserId(7);
        when(interactionService.addToWatchlist(7, 42)).thenReturn(true);

        ChatToolResult result = executor.execute(user,
            plan("ADD_WATCHLIST", "{\"movieId\":42}"), List.of(movie)).getFirst();

        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("thành công"));
        assertNull(result.getClientAction(), "frontend must not execute the mutation a second time");
        verify(interactionService).addToWatchlist(7, 42);
    }

    @Test
    void watchlistToolCanResolveARealMovieByTitle() {
        User user = new User();
        user.setUserId(7);
        when(movieRepository.searchByTitleOrGenre("Interstellar")).thenReturn(List.of(movie));
        when(interactionService.addToWatchlist(7, 42)).thenReturn(true);

        ChatToolResult result = executor.execute(user,
            plan("ADD_WATCHLIST", "{\"query\":\"Interstellar\"}"), Collections.emptyList()).getFirst();

        assertTrue(result.isSuccess());
        verify(interactionService).addToWatchlist(7, 42);
    }

    @Test
    void responderUsesVerifiedFailureInsteadOfClaimingSuccess() {
        ChatModelClient model = mock(ChatModelClient.class);
        when(model.isEnabled()).thenReturn(true);
        ChatAgentResponder responder = new ChatAgentResponder(model);
        ChatAgentPlan plan = new ChatAgentPlan();
        plan.setIntent("USER_ACTION");
        ChatToolResult failure = new ChatToolResult("ADD_WATCHLIST", false,
            "Bạn cần đăng nhập để thêm phim vào Watchlist.", Collections.emptyList(), null);

        String response = responder.respond("lưu phim này", "guest", plan, List.of(failure));

        assertEquals(failure.getMessage(), response);
        verify(model, never()).complete(anyList(), any(), anyInt(), anyDouble());
    }

    @Test
    void plannerParsesModelSelectedTools() {
        ChatModelClient model = mock(ChatModelClient.class);
        when(model.isEnabled()).thenReturn(true);
        when(model.complete(anyList(), anyMap(), eq(650), eq(0.1))).thenReturn("""
            {"intent":"MOVIE_RECOMMENDATION","confidence":0.94,"missingInfo":"",
             "responseGuidance":"Gợi ý phim khoa học viễn tưởng dễ xem.",
             "toolCalls":[{"name":"RECOMMEND_MOVIES","arguments":"{\\\"movieIds\\\":[42]}"}]}
            """);
        ChatAgentPlanner planner = new ChatAgentPlanner(model);

        ChatAgentPlan plan = planner.plan("gợi ý phim", "guest", "ID=42 | Interstellar", List.of()).orElseThrow();

        assertEquals("MOVIE_RECOMMENDATION", plan.getIntent());
        assertEquals("RECOMMEND_MOVIES", plan.getToolCalls().getFirst().getName());
        assertEquals("{\"movieIds\":[42]}", plan.getToolCalls().getFirst().getArguments());
    }

    @Test
    void providerKeepsBackwardCompatibilityWithExistingOpenAiKey() {
        OpenAICompatibleChatModelClient client = new OpenAICompatibleChatModelClient();
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.openai.com/v1");
        ReflectionTestUtils.setField(client, "apiKey", "");
        ReflectionTestUtils.setField(client, "legacyApiKey", "existing-railway-secret");

        assertTrue(client.isEnabled());
    }

    @Test
    void fallbackRouterStillUsesBackendToolsWhenModelIsDisabled() {
        AIChatService chatService = new AIChatService();
        ReflectionTestUtils.setField(chatService, "intentClassifier", new ChatIntentClassifier());
        ReflectionTestUtils.setField(chatService, "chatHelpService", new ChatHelpService());

        ChatAgentPlan plan = ReflectionTestUtils.invokeMethod(chatService, "buildDeterministicAgentPlan",
            "Lưu phim Interstellar vào watchlist giúp tôi", Collections.emptyList());

        assertNotNull(plan);
        assertEquals("USER_ACTION", plan.getIntent());
        assertEquals("ADD_WATCHLIST", plan.getToolCalls().getFirst().getName());
        assertTrue(plan.getToolCalls().getFirst().getArguments().contains("Interstellar"));
    }

    @Test
    void fallbackRouterSendsTimelineQuestionsToMovieDetailTool() {
        AIChatService chatService = new AIChatService();
        ReflectionTestUtils.setField(chatService, "intentClassifier", new ChatIntentClassifier());
        ReflectionTestUtils.setField(chatService, "chatHelpService", new ChatHelpService());

        ChatAgentPlan plan = ReflectionTestUtils.invokeMethod(chatService, "buildDeterministicAgentPlan",
            "Timeline phim Interstellar", Collections.emptyList());

        assertNotNull(plan);
        assertEquals("VIDEO_QA", plan.getIntent());
        assertEquals("GET_MOVIE_DETAIL", plan.getToolCalls().getFirst().getName());
        assertTrue(plan.getToolCalls().getFirst().getArguments().contains("Interstellar"));
    }

    private ChatAgentPlan plan(String tool, String arguments) {
        ChatAgentPlan plan = new ChatAgentPlan();
        plan.setIntent("USER_ACTION");
        plan.setToolCalls(List.of(new ChatAgentPlan.ToolCall(tool, arguments)));
        return plan;
    }
}
