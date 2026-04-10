package com.example.movierecommendation.service;

import com.example.movierecommendation.algorithm.RecommendationEngine;
import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.repository.MovieRepository;
import com.example.movierecommendation.repository.WatchHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    @Autowired private RecommendationEngine engine;
    @Autowired private MovieRepository movieRepository;
    @Autowired private WatchHistoryRepository watchHistoryRepository;
    @Autowired private OpenAIService openAIService;

    @Cacheable(value = "user_ai_recommendations", key = "#userId", unless = "#result == null or #result.isEmpty()")
    public List<Movie> getPersonalizedRecommendations(Integer userId) {
        List<Movie> hybrid = engine.getRecommendations(userId);

        if (!openAIService.isEnabled()) return hybrid;

        try {
            List<Integer> watchedIds = watchHistoryRepository.findWatchedMovieIdsByUserId(userId);
            List<Integer> exclude = watchedIds.isEmpty() ? Collections.singletonList(-1) : watchedIds;
            List<Movie> candidates = movieRepository.findMostWatchedMoviesExcluding(exclude, PageRequest.of(0, 20));
            List<String> aiTitles = openAIService.getAIRecommendedTitles(userId, candidates);

            if (aiTitles == null || aiTitles.isEmpty()) return hybrid;

            // FIX: Use structured lookup Map
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

            // Merge: AI movies lên đầu, hybrid sau, dedup bằng Set
            List<Movie> merged = new ArrayList<>(aiMovies);
            Set<Integer> watchedSet = new HashSet<>(watchedIds);
            for (Movie m : hybrid) {
                if (watchedSet.contains(m.getMovieId())) continue;
                if (seen.add(m.getMovieId())) merged.add(m);
            }

            merged.removeIf(m -> watchedSet.contains(m.getMovieId()));

            return merged.size() > 20 ? merged.subList(0, 20) : merged;

        } catch (Exception e) {
            log.warn("AI recommendation fallback for user {}: {}", userId, e.getMessage());
            return hybrid;
        }
    }

    public List<Movie> getSimilarMovies(Movie movie, Integer userId) {
        return engine.getSimilarMovies(movie, userId);
    }

    public List<Movie> getGenreBasedRecommendations(Integer userId) {
        return engine.getGenreBasedRecommendations(userId);
    }

    public List<Movie> getTrendingMovies() {
        return engine.getTrendingMovies(10);
    }

    public List<Movie> getTopRatedMovies() {
        return movieRepository.findTopRatedMovies(PageRequest.of(0, 10));
    }

    public List<Movie> getNewReleases() {
        return movieRepository.findNewMoviesNotWatched(
            Collections.singletonList(-1), PageRequest.of(0, 10));
    }
}