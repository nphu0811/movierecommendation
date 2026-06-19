package com.example.movierecommendation.algorithm;

import com.example.movierecommendation.entity.Genre;
import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.entity.Rating;
import com.example.movierecommendation.repository.MovieRepository;
import com.example.movierecommendation.repository.RatingRepository;
import com.example.movierecommendation.repository.UserPreferenceRepository;
import com.example.movierecommendation.repository.WatchHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationEnginePersonalizationTest {

    @Mock RatingRepository ratingRepository;
    @Mock WatchHistoryRepository watchHistoryRepository;
    @Mock MovieRepository movieRepository;
    @Mock UserPreferenceRepository userPreferenceRepository;

    private RecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RecommendationEngine();
        ReflectionTestUtils.setField(engine, "ratingRepository", ratingRepository);
        ReflectionTestUtils.setField(engine, "watchHistoryRepository", watchHistoryRepository);
        ReflectionTestUtils.setField(engine, "movieRepository", movieRepository);
        ReflectionTestUtils.setField(engine, "userPreferenceRepository", userPreferenceRepository);
        ReflectionTestUtils.setField(engine, "alpha", 0.40);
        ReflectionTestUtils.setField(engine, "beta", 0.40);
        ReflectionTestUtils.setField(engine, "gamma", 0.20);
        ReflectionTestUtils.setField(engine, "maxRecommendations", 20);
        ReflectionTestUtils.setField(engine, "topGenres", 5);
        ReflectionTestUtils.setField(engine, "candidateLimit", 200);
        ReflectionTestUtils.setField(engine, "popularLimit", 50);
    }

    @Test
    void usersWithDifferentGenreHistoryReceiveDifferentResults() {
        Genre action = genre(1, "Action");
        Genre comedy = genre(2, "Comedy");
        Movie ratedAction = movie(10, "Rated Action", action);
        Movie ratedComedy = movie(20, "Rated Comedy", comedy);
        Movie actionCandidate = movie(101, "Action Candidate", action);
        Movie comedyCandidate = movie(202, "Comedy Candidate", comedy);

        when(ratingRepository.findByUserUserId(1)).thenReturn(List.of(rating(ratedAction, 5.0)));
        when(ratingRepository.findByUserUserId(2)).thenReturn(List.of(rating(ratedComedy, 5.0)));
        when(ratingRepository.findRatedMovieIdsByUserId(1)).thenReturn(List.of(10));
        when(ratingRepository.findRatedMovieIdsByUserId(2)).thenReturn(List.of(20));
        when(watchHistoryRepository.findWatchedMovieIdsByUserId(anyInt())).thenReturn(Collections.emptyList());
        when(ratingRepository.findUserIdsWithCommonMovies(anyList(), anyInt())).thenReturn(Collections.emptyList());
        when(movieRepository.findMostWatchedMoviesExcludingUserInteractions(anyInt(), org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenReturn(Collections.emptyList());
        when(movieRepository.findByGenreIdsExcludingUserInteractions(eq(List.of(1)), eq(1), org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenReturn(List.of(actionCandidate));
        when(movieRepository.findByGenreIdsExcludingUserInteractions(eq(List.of(2)), eq(2), org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenReturn(List.of(comedyCandidate));
        when(movieRepository.findAllByIdWithGenres(List.of(101))).thenReturn(List.of(actionCandidate));
        when(movieRepository.findAllByIdWithGenres(List.of(202))).thenReturn(List.of(comedyCandidate));

        assertEquals(List.of(101), engine.getRecommendations(1).stream().map(Movie::getMovieId).toList());
        assertEquals(List.of(202), engine.getRecommendations(2).stream().map(Movie::getMovieId).toList());
    }

    private Genre genre(int id, String name) {
        Genre genre = new Genre();
        genre.setGenreId(id);
        genre.setGenreName(name);
        return genre;
    }

    private Movie movie(int id, String title, Genre genre) {
        Movie movie = new Movie();
        movie.setMovieId(id);
        movie.setTitle(title);
        movie.setGenres(List.of(genre));
        return movie;
    }

    private Rating rating(Movie movie, double score) {
        Rating rating = new Rating();
        rating.setMovie(movie);
        rating.setRating(score);
        return rating;
    }
}
