package com.example.movierecommendation.service;

import com.example.movierecommendation.algorithm.RecommendationEngine;
import com.example.movierecommendation.entity.RecommendationLog;
import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.entity.UserRecommendation;
import com.example.movierecommendation.repository.MovieRepository;
import com.example.movierecommendation.repository.RecommendationLogRepository;
import com.example.movierecommendation.repository.UserRecommendationRepository;
import com.example.movierecommendation.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    @Autowired private RecommendationEngine engine;
    @Autowired private MovieRepository movieRepository;
    @Autowired private OpenAIService openAIService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRecommendationRepository userRecommendationRepository;
    @Autowired private RecommendationLogRepository recommendationLogRepository;

    private List<Movie> removeExcludedMovies(Integer userId, List<Movie> movies) {
        if (movies == null || movies.isEmpty()) return movies == null ? Collections.emptyList() : movies;
        Set<Integer> ex = engine.getExcludedMovieIdsForRecommendations(userId);
        if (ex.isEmpty()) return movies;
        List<Movie> out = new ArrayList<>();
        for (Movie m : movies) {
            if (!ex.contains(m.getMovieId())) out.add(m);
        }
        return out;
    }

    private static List<Movie> limitSize(List<Movie> list, int max) {
        if (list.size() <= max) return list;
        return new ArrayList<>(list.subList(0, max));
    }

    @Transactional
    public List<Movie> getPersonalizedRecommendations(Integer userId) {
        long started = System.currentTimeMillis();
        List<Movie> hybrid = removeExcludedMovies(userId, engine.getRecommendations(userId));
        List<Movie> result = hybrid;

        if (!openAIService.isEnabled()) {
            persistRecommendations(userId, "HYBRID", result, started, "OpenAI disabled");
            return result;
        }

        try {
            List<Movie> candidates = movieRepository.findMostWatchedMoviesExcludingUserInteractions(
                userId, PageRequest.of(0, 20));
            List<String> aiTitles = openAIService.getAIRecommendedTitles(userId, candidates);

            if (aiTitles == null || aiTitles.isEmpty()) {
                persistRecommendations(userId, "HYBRID", result, started, "OpenAI returned no titles");
                return result;
            }

            // Use structured lookup Map
            Map<String, Movie> titleIndex = new HashMap<>();
            for (Movie m : candidates) {
                titleIndex.put(m.getTitle().toLowerCase(), m);
            }

            List<Movie> aiMovies = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();

            for (String title : aiTitles) {
                String key = title.toLowerCase();
                Movie match = titleIndex.get(key);
                if (match == null) {
                    match = titleIndex.entrySet().stream()
                        .filter(e -> e.getKey().contains(key) || key.contains(e.getKey()))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null);
                }
                if (match != null && seen.add(match.getMovieId())) {
                    aiMovies.add(match);
                }
            }

            List<Movie> merged = new ArrayList<>(aiMovies);
            for (Movie m : hybrid) {
                if (seen.add(m.getMovieId())) merged.add(m);
            }

            result = limitSize(removeExcludedMovies(userId, merged), 20);
            persistRecommendations(userId, "HYBRID_AI", result, started, "Generated with OpenAI reranking");
            return result;

        } catch (Exception e) {
            log.warn("AI recommendation fallback for user {}: {}", userId, e.getMessage());
            persistRecommendations(userId, "HYBRID", result, started, "AI fallback: " + e.getMessage());
            return result;
        }
    }

    private void persistRecommendations(Integer userId, String algorithmType, List<Movie> movies, long started, String notes) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                userRecommendationRepository.deleteByUserUserIdAndAlgorithmType(userId, algorithmType);
                List<UserRecommendation> rows = new ArrayList<>();
                Set<Integer> seenIds = new HashSet<>();
                for (int i = 0; i < movies.size(); i++) {
                    Movie m = movies.get(i);
                    if (seenIds.add(m.getMovieId())) {
                        UserRecommendation row = new UserRecommendation();
                        row.setUser(user);
                        row.setMovie(m);
                        row.setAlgorithmType(algorithmType);
                        row.setScore(BigDecimal.valueOf(Math.max(0.01, movies.size() - i)));
                        rows.add(row);
                    }
                }
                userRecommendationRepository.saveAll(rows);
            }

            RecommendationLog logRow = new RecommendationLog();
            logRow.setAlgorithmName(algorithmType);
            logRow.setTotalUsers(1);
            logRow.setTotalMovies(movies.size());
            logRow.setExecutionTimeMs((int) (System.currentTimeMillis() - started));
            logRow.setNotes(notes);
            recommendationLogRepository.save(logRow);
        } catch (Exception e) {
            log.debug("Could not persist recommendation metadata for user {}: {}", userId, e.getMessage());
        }
    }

    public List<Movie> getSimilarMovies(Movie movie, Integer userId) {
        return engine.getSimilarMovies(movie, userId);
    }

    @Transactional(readOnly = true)
    public List<Movie> getGenreBasedRecommendations(Integer userId) {
        return removeExcludedMovies(userId, engine.getGenreBasedRecommendations(userId));
    }

    public List<Movie> getTrendingMovies() {
        return engine.getTrendingMovies(10);
    }

    /** Trending nhưng bỏ phim user đã xem / đã rate (khi đã đăng nhập). */
    public List<Movie> getTrendingMoviesForUser(Integer userId) {
        return movieRepository.findMostWatchedMoviesExcludingUserInteractions(userId, PageRequest.of(0, 10));
    }

    public List<Movie> getTopRatedMovies() {
        return movieRepository.findTopRatedMovies(PageRequest.of(0, 10));
    }

    public List<Movie> getTopRatedMoviesForUser(Integer userId) {
        return movieRepository.findTopRatedMoviesExcludingUserInteractions(userId, PageRequest.of(0, 10));
    }

    public List<Movie> getNewReleases() {
        return movieRepository.findNewMoviesNotWatched(
            Collections.singletonList(-1), PageRequest.of(0, 10));
    }
}
