package com.example.movierecommendation.service;

import com.example.movierecommendation.algorithm.RecommendationEngine;
import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.repository.MovieRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    @Autowired private RecommendationEngine engine;
    @Autowired private MovieRepository movieRepository;
    @Autowired private OpenAIService openAIService;

    public List<Movie> getPersonalizedRecommendations(Integer userId) {
        List<Movie> hybrid = engine.getRecommendations(userId);

        if (!openAIService.isEnabled()) return hybrid;

        try {
            // FIX: Không gọi findAll() - chỉ lấy trending 100 phim để AI chọn
            // Đủ context, tránh load toàn DB vào RAM
            List<Movie> candidates = movieRepository.findMostWatchedMovies(PageRequest.of(0, 100));
            List<String> aiTitles = openAIService.getAIRecommendedTitles(userId, candidates);

            if (aiTitles.isEmpty()) return hybrid;

            // FIX: Dedup dùng Set<Integer> thay vì nested loop O(n²)
            Map<String, Movie> titleIndex = new HashMap<>();
            for (Movie m : candidates) {
                titleIndex.put(m.getTitle().toLowerCase(), m);
            }

            List<Movie> aiMovies = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();

            for (String title : aiTitles) {
                String key = title.toLowerCase();
                Movie match = titleIndex.get(key);
                // Fuzzy match nếu exact không tìm được
                if (match == null) {
                    for (Map.Entry<String, Movie> e : titleIndex.entrySet()) {
                        if (e.getKey().contains(key) || key.contains(e.getKey())) {
                            match = e.getValue();
                            break;
                        }
                    }
                }
                if (match != null && seen.add(match.getMovieId())) {
                    aiMovies.add(match);
                }
            }

            // Merge: AI movies lên đầu, hybrid sau, dedup bằng Set
            List<Movie> merged = new ArrayList<>(aiMovies);
            for (Movie m : hybrid) {
                if (seen.add(m.getMovieId())) merged.add(m);
            }

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