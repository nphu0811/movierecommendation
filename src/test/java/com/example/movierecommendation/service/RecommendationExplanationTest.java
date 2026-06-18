package com.example.movierecommendation.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.movierecommendation.entity.*;
import com.example.movierecommendation.repository.RatingRepository;
import com.example.movierecommendation.repository.WatchHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

@ExtendWith(MockitoExtension.class)
public class RecommendationExplanationTest {

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private WatchHistoryRepository watchHistoryRepository;

    @InjectMocks
    private RecommendationService recommendationService;

    @Test
    public void testPopulateExplanations_ColdStart() {
        // Mock empty user history
        when(ratingRepository.findByUserUserId(1)).thenReturn(Collections.emptyList());
        when(watchHistoryRepository.findByUserUserIdOrderByWatchedAtAsc(1)).thenReturn(Collections.emptyList());

        Movie m1 = new Movie();
        m1.setMovieId(101);
        m1.setTitle("Popular Movie");
        List<Movie> recommended = List.of(m1);

        recommendationService.populateExplanations(1, recommended, Collections.emptySet());

        assertEquals("Vì phim này đang phổ biến và được đánh giá cao trên hệ thống.", m1.getRecommendationReason());
    }

    @Test
    public void testPopulateExplanations_AIPick() {
        Movie m1 = new Movie();
        m1.setMovieId(101);
        m1.setTitle("AI Movie");
        List<Movie> recommended = List.of(m1);

        recommendationService.populateExplanations(1, recommended, Set.of("AI Movie"));

        assertEquals("Vì AI chọn phim này phù hợp với sở thích của bạn.", m1.getRecommendationReason());
    }

    @Test
    public void testPopulateExplanations_GenreOverlap() {
        // User liked Action (GenreId=1)
        Genre action = new Genre();
        action.setGenreId(1);
        action.setGenreName("Action");

        Movie watchedMovie = new Movie();
        watchedMovie.setGenres(List.of(action));

        Rating r1 = new Rating();
        r1.setMovie(watchedMovie);
        r1.setRating(5.0);

        when(ratingRepository.findByUserUserId(1)).thenReturn(List.of(r1));
        when(watchHistoryRepository.findByUserUserIdOrderByWatchedAtAsc(1)).thenReturn(Collections.emptyList());

        // Recommended movie is Action
        Movie m1 = new Movie();
        m1.setMovieId(101);
        m1.setTitle("Action Movie");
        m1.setGenres(List.of(action));

        recommendationService.populateExplanations(1, List.of(m1), Collections.emptySet());

        assertEquals("Vì bạn đã xem/đánh giá cao nhiều phim thuộc thể loại Action.", m1.getRecommendationReason());
    }
}
