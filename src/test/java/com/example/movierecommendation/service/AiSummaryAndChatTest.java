package com.example.movierecommendation.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.movierecommendation.entity.*;
import com.example.movierecommendation.repository.MovieRepository;
import com.example.movierecommendation.repository.GenreRepository;

import com.example.movierecommendation.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class AiSummaryAndChatTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private OpenAIService openAIService;

    @InjectMocks
    private MovieService movieService;

    @Test
    public void testGetMovieAiSummary_Cached() {
        Movie movie = new Movie();
        movie.setMovieId(1);
        movie.setTitle("Inception");
        movie.setAiSummary("Cached AI Summary content");

        when(movieRepository.findById(1)).thenReturn(Optional.of(movie));

        String result = movieService.getMovieAiSummary(1);
        assertEquals("Cached AI Summary content", result);
        verify(openAIService, never()).generateMovieSummary(anyString(), anyString());
    }

    @Test
    public void testGetMovieAiSummary_GenerateFallback() {
        Movie movie = new Movie();
        movie.setMovieId(2);
        movie.setTitle("Interstellar");
        movie.setDescription("A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival.");
        movie.setReleaseYear(2014);
        movie.setAverageRating(4.8);
        movie.setRatingCount(200);

        when(movieRepository.findById(2)).thenReturn(Optional.of(movie));
        when(openAIService.generateMovieSummary(anyString(), anyString())).thenReturn(null);
        when(movieRepository.save(any(Movie.class))).thenAnswer(inv -> inv.getArgument(0));

        String result = movieService.getMovieAiSummary(2);
        assertNotNull(result);
        assertTrue(result.contains("Interstellar"));
        assertTrue(result.contains("2014"));
        assertTrue(result.contains("đánh giá trung bình 4.8/5"));
    }

    @Test
    public void testChatAboutVideo_SummaryRequestFallback() {
        AIChatService chatService = new AIChatService();
        
        Movie movie = new Movie();
        movie.setTitle("The Matrix");
        movie.setGenres(Collections.emptyList());
        movie.setDescription("Neo learns the truth about reality.");
        
        String response = chatService.chatAboutVideo(null, movie, "Hãy tóm tắt video này");
        assertNotNull(response);
        assertTrue(response.contains("Dòng thời gian tóm tắt (ước lượng)"));
        assertTrue(response.contains("The Matrix"));
        assertTrue(response.contains("[00:10]"));
    }

    @Test
    public void testChatAboutVideo_OutOfScopeAndGreeting() {
        AIChatService chatService = new AIChatService();
        ChatIntentClassifier classifier = new ChatIntentClassifier();
        ChatHelpService helpService = new ChatHelpService();
        
        org.springframework.test.util.ReflectionTestUtils.setField(chatService, "intentClassifier", classifier);
        org.springframework.test.util.ReflectionTestUtils.setField(chatService, "chatHelpService", helpService);

        Movie movie = new Movie();
        movie.setTitle("The Matrix");

        String oosResponse = chatService.chatAboutVideo(null, movie, "cách nấu phở");
        assertTrue(oosResponse.toLowerCase().contains("xin lỗi") || oosResponse.toLowerCase().contains("ngoài phạm vi"));

        String greetResponse = chatService.chatAboutVideo(null, movie, "hello");
        assertTrue(greetResponse.toLowerCase().contains("xin chào") || greetResponse.toLowerCase().contains("xin chao"));
    }

    @Test
    public void testRecommendMovies_EmptyCandidatesNoResults() {
        AIChatService chatService = new AIChatService();
        ChatIntentClassifier classifier = new ChatIntentClassifier();
        ChatHelpService helpService = new ChatHelpService();
        
        org.springframework.test.util.ReflectionTestUtils.setField(chatService, "intentClassifier", classifier);
        org.springframework.test.util.ReflectionTestUtils.setField(chatService, "chatHelpService", helpService);
        
        MovieRepository movieRepository = mock(MovieRepository.class);
        when(movieRepository.searchByTitleOrGenre(anyString())).thenReturn(Collections.emptyList());
        org.springframework.test.util.ReflectionTestUtils.setField(chatService, "movieRepository", movieRepository);

        GenreRepository genreRepository = mock(GenreRepository.class);
        when(genreRepository.findAll()).thenReturn(Collections.emptyList());
        org.springframework.test.util.ReflectionTestUtils.setField(chatService, "genreRepository", genreRepository);

        ChatResponse response = chatService.recommendMovies(null, "tìm phim doraemon cho tôi");
        assertNotNull(response);
        assertEquals("TEXT", response.getType());
        assertTrue(response.getMessage().contains("Rất tiếc"));
        assertTrue(response.getMessage().contains("doraemon"));
        assertTrue(response.getMovies().isEmpty());
    }
}
