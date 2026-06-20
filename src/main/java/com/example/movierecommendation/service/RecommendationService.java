package com.example.movierecommendation.service;

import com.example.movierecommendation.algorithm.RecommendationEngine;
import com.example.movierecommendation.entity.RecommendationLog;
import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.entity.UserRecommendation;
import com.example.movierecommendation.entity.Rating;
import com.example.movierecommendation.entity.WatchHistory;
import com.example.movierecommendation.entity.Genre;
import com.example.movierecommendation.repository.*;
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
    @Autowired private RatingRepository ratingRepository;
    @Autowired private WatchHistoryRepository watchHistoryRepository;
    @Autowired @org.springframework.context.annotation.Lazy private RecommendationService self;

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

    public List<Movie> getPersonalizedRecommendations(Integer userId) {
        String algo = openAIService.isEnabled() ? "HYBRID_AI" : "HYBRID";
        List<UserRecommendation> dbRecs = userRecommendationRepository.findByUserUserIdAndAlgorithmTypeOrderByScoreDescGeneratedAtDesc(
            userId, algo, PageRequest.of(0, 20));
        
        if (dbRecs != null && !dbRecs.isEmpty()) {
            List<Movie> movies = new ArrayList<>();
            for (UserRecommendation ur : dbRecs) {
                Movie m = ur.getMovie();
                if (m != null) {
                    m.setRecommendationReason(ur.getRecommendationReason());
                    movies.add(m);
                }
            }
            List<Movie> filtered = removeExcludedMovies(userId, movies);
            if (!filtered.isEmpty()) {
                return filtered;
            }
        }
        
        return computeAndPersistPersonalizedRecommendations(userId);
    }

    public List<Movie> computeAndPersistPersonalizedRecommendations(Integer userId) {
        long started = System.currentTimeMillis();
        List<Movie> hybrid = removeExcludedMovies(userId, engine.getRecommendations(userId));
        List<Movie> result = hybrid;

        if (!openAIService.isEnabled()) {
            populateExplanations(userId, result, Collections.emptyMap());
            self.persistRecommendations(userId, "HYBRID", result, started, "OpenAI disabled");
            return result;
        }

        try {
            Map<Integer, String> aiRecsWithReasons = openAIService.getAIRecommendationsWithReasons(userId, hybrid);

            if (aiRecsWithReasons == null || aiRecsWithReasons.isEmpty()) {
                populateExplanations(userId, result, Collections.emptyMap());
                self.persistRecommendations(userId, "HYBRID", result, started, "OpenAI returned no recommendations");
                return result;
            }

            // Use structured lookup Map
            Map<Integer, Movie> movieMap = new HashMap<>();
            for (Movie m : hybrid) {
                movieMap.put(m.getMovieId(), m);
            }

            List<Movie> aiMovies = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();

            for (Integer movieId : aiRecsWithReasons.keySet()) {
                Movie match = movieMap.get(movieId);
                if (match != null && seen.add(match.getMovieId())) {
                    aiMovies.add(match);
                }
            }

            List<Movie> merged = new ArrayList<>(aiMovies);
            for (Movie m : hybrid) {
                if (seen.add(m.getMovieId())) merged.add(m);
            }

            result = limitSize(removeExcludedMovies(userId, merged), 20);
            populateExplanations(userId, result, aiRecsWithReasons);
            self.persistRecommendations(userId, "HYBRID_AI", result, started, "Generated with OpenAI reranking and custom explanations");
            return result;

        } catch (Exception e) {
            log.warn("AI recommendation fallback for user {}: {}", userId, e.getMessage());
            populateExplanations(userId, result, Collections.emptyMap());
            self.persistRecommendations(userId, "HYBRID", result, started, "AI fallback: " + e.getMessage());
            return result;
        }
    }

    public void populateExplanations(Integer userId, List<Movie> movies, Map<Integer, String> aiRecsWithReasons) {
        if (movies == null || movies.isEmpty()) return;

        // Get user ratings and watch history
        List<Rating> ratings = ratingRepository.findByUserUserId(userId);
        List<WatchHistory> history = watchHistoryRepository.findByUserUserIdOrderByWatchedAtAsc(userId);
        
        // If user is new (no history)
        boolean hasHistory = !ratings.isEmpty() || !history.isEmpty();

        // Top genres the user likes
        Map<Integer, Double> genreProfile = new HashMap<>();
        for (Rating r : ratings) {
            Movie movie = r.getMovie();
            if (movie == null || movie.getGenres() == null) continue;
            for (Genre g : movie.getGenres()) {
                genreProfile.merge(g.getGenreId(), r.getRating(), Double::sum);
            }
        }
        for (WatchHistory wh : history) {
            Movie movie = wh.getMovie();
            if (movie == null || movie.getGenres() == null) continue;
            double weight = 1.0;
            if (wh.getProgress() != null && wh.getProgress() > 80) weight += 2.0;
            for (Genre g : movie.getGenres()) {
                genreProfile.merge(g.getGenreId(), weight, Double::sum);
            }
        }

        // Sort genres by weight to find favorite genres
        List<Map.Entry<Integer, Double>> favoriteGenres = new ArrayList<>(genreProfile.entrySet());
        favoriteGenres.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        Set<Integer> topGenreIds = new HashSet<>();
        for (int i = 0; i < Math.min(3, favoriteGenres.size()); i++) {
            topGenreIds.add(favoriteGenres.get(i).getKey());
        }

        for (Movie m : movies) {
            if (aiRecsWithReasons != null && aiRecsWithReasons.containsKey(m.getMovieId())) {
                m.setRecommendationReason(aiRecsWithReasons.get(m.getMovieId()));
                continue;
            }

            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            if (!hasHistory) {
                m.setRecommendationReason(isVi 
                    ? "Vì phim này đang phổ biến và được đánh giá cao trên hệ thống."
                    : "Because this movie is popular and highly rated on the platform.");
                continue;
            }

            // Check genre overlap
            List<String> matchingGenres = new ArrayList<>();
            if (m.getGenres() != null) {
                for (Genre g : m.getGenres()) {
                    if (topGenreIds.contains(g.getGenreId())) {
                        matchingGenres.add(g.getGenreName());
                    }
                }
            }

            if (!matchingGenres.isEmpty()) {
                m.setRecommendationReason(isVi 
                    ? "Vì bạn đã xem/đánh giá cao nhiều phim thuộc thể loại " + String.join(", ", matchingGenres) + "."
                    : "Because you have watched/highly rated many movies in the " + String.join(", ", matchingGenres) + " genre(s).");
            } else {
                m.setRecommendationReason(isVi 
                    ? "Vì những người dùng có gu giống bạn cũng đánh giá cao phim này."
                    : "Because users with similar tastes also highly rated this movie.");
            }
        }
    }

    @Transactional
    public void persistRecommendations(Integer userId, String algorithmType, List<Movie> movies, long started, String notes) {
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
                        row.setRecommendationReason(m.getRecommendationReason());
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

    /** Trending but excludes movies the user has watched / rated (when logged in). */
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

    public List<RecommendationLog> getLatestRecommendationLogs(int limit) {
        try {
            return recommendationLogRepository.findAll(PageRequest.of(0, limit, org.springframework.data.domain.Sort.by("generatedAt").descending())).getContent();
        } catch (Exception e) {
            log.error("Failed to fetch latest recommendation logs", e);
            return Collections.emptyList();
        }
    }

    public List<Object[]> getTopRecommendedMovies(int limit) {
        try {
            return userRecommendationRepository.findTopRecommendedMovies(PageRequest.of(0, limit));
        } catch (Exception e) {
            log.error("Failed to fetch top recommended movies", e);
            return Collections.emptyList();
        }
    }

    public List<Object[]> getAlgorithmDistribution() {
        try {
            return userRecommendationRepository.findAlgorithmDistribution();
        } catch (Exception e) {
            log.error("Failed to fetch algorithm distribution", e);
            return Collections.emptyList();
        }
    }

    @org.springframework.cache.annotation.CacheEvict(value = "recommendations", key = "#userId")
    public void evictRecommendationsCache(Integer userId) {
        log.info("Evicted recommendations cache for user {}", userId);
    }
}
