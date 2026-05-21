package com.example.movierecommendation.service;

import com.example.movierecommendation.dto.SearchSuggestionDto;
import com.example.movierecommendation.dto.SearchTrendDto;
import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.entity.SearchHistory;
import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.repository.MovieRepository;
import com.example.movierecommendation.repository.SearchHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class SearchHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(SearchHistoryService.class);

    @Autowired
    private SearchHistoryRepository searchHistoryRepository;

    @Autowired
    private MovieRepository movieRepository;

    /**
     * Log a search query asynchronously or in a try-catch block so that database
     * errors never interrupt the user's search process.
     */
    @Transactional
    public SearchHistory logSearch(User user, String searchQuery, Integer resultCount,
                                  String sessionId, String ipAddress, String userAgent,
                                  String searchSource, Long latencyMs) {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return null;
        }

        try {
            String trimmedQuery = searchQuery.trim();
            String normalizedQuery = trimmedQuery.toLowerCase().replaceAll("\\s+", " ");

            // Truncate to maximum database VARCHAR length if necessary
            if (trimmedQuery.length() > 255) {
                trimmedQuery = trimmedQuery.substring(0, 255);
            }
            if (normalizedQuery.length() > 255) {
                normalizedQuery = normalizedQuery.substring(0, 255);
            }

            SearchHistory searchHistory = new SearchHistory();
            searchHistory.setUser(user);
            searchHistory.setSearchQuery(trimmedQuery);
            searchHistory.setNormalizedQuery(normalizedQuery);
            searchHistory.setResultCount(resultCount);
            searchHistory.setSessionId(sessionId);
            searchHistory.setIpAddress(ipAddress);
            searchHistory.setUserAgent(userAgent);
            searchHistory.setSearchSource(searchSource);
            searchHistory.setLatencyMs(latencyMs != null ? latencyMs.intValue() : null);
            searchHistory.setCreatedAt(LocalDateTime.now());

            return searchHistoryRepository.save(searchHistory);
        } catch (Exception e) {
            logger.warn("Failed to log search history for query: {}", searchQuery, e);
            return null;
        }
    }

    /**
     * Mark a search history record as clicked, linking a specific movie.
     * Implements strict access controls:
     * - Logged-in searches can only be updated by the same user.
     * - Guest searches can only be updated by the same session_id.
     * - Do not allow updating very old search records (e.g. older than 24 hours).
     * - Do not overwrite clicked_movie_id if it is already set.
     */
    @Transactional
    public boolean markClickedMovie(Long searchId, Integer movieId, User currentUser, String sessionId) {
        if (searchId == null || movieId == null) {
            return false;
        }

        try {
            Optional<SearchHistory> optSearch = searchHistoryRepository.findById(searchId);
            if (optSearch.isEmpty()) {
                logger.warn("Search record not found with ID: {}", searchId);
                return false;
            }

            SearchHistory searchHistory = optSearch.get();

            // 1. Do not overwrite if clicked movie is already set
            if (searchHistory.getClickedMovie() != null) {
                logger.warn("Search record {} already has a clicked movie set.", searchId);
                return false;
            }

            // 2. Reject if record is older than 24 hours
            if (searchHistory.getCreatedAt() != null &&
                    searchHistory.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24))) {
                logger.warn("Search record {} is too old to track click (created at {}).", searchId, searchHistory.getCreatedAt());
                return false;
            }

            // 3. Ownership / security validation
            if (searchHistory.getUser() != null) {
                // Logged-in search: must match current user
                if (currentUser == null || !searchHistory.getUser().getUserId().equals(currentUser.getUserId())) {
                    logger.warn("Access denied: Search record {} belongs to user {}, but requested by user {}",
                            searchId, searchHistory.getUser().getUserId(),
                            currentUser != null ? currentUser.getUserId() : "GUEST");
                    return false;
                }
            } else {
                // Guest search: must match session ID
                if (sessionId == null || !sessionId.equals(searchHistory.getSessionId())) {
                    logger.warn("Access denied: Guest search record {} belongs to session {}, but requested by session {}",
                            searchId, searchHistory.getSessionId(), sessionId);
                    return false;
                }
            }

            // 4. Verify movie exists
            Optional<Movie> optMovie = movieRepository.findById(movieId);
            if (optMovie.isEmpty()) {
                logger.warn("Movie not found with ID: {}", movieId);
                return false;
            }

            searchHistory.setClickedMovie(optMovie.get());
            searchHistory.setClickedAt(LocalDateTime.now());
            searchHistoryRepository.save(searchHistory);
            return true;

        } catch (Exception e) {
            logger.error("Error marking clicked movie for search ID: {}", searchId, e);
            return false;
        }
    }

    /**
     * Retrieve trending searches within the specified range ("24h", "7d", "30d").
     */
    public List<SearchTrendDto> getTrendingSearches(String range) {
        LocalDateTime since;
        if ("7d".equalsIgnoreCase(range)) {
            since = LocalDateTime.now().minusDays(7);
        } else if ("30d".equalsIgnoreCase(range)) {
            since = LocalDateTime.now().minusDays(30);
        } else {
            since = LocalDateTime.now().minusDays(1); // default "24h"
        }

        try {
            List<Object[]> rawTrends = searchHistoryRepository.findTrendingSearches(since, PageRequest.of(0, 10));
            List<SearchTrendDto> trends = new ArrayList<>();
            for (Object[] row : rawTrends) {
                if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                    trends.add(new SearchTrendDto((String) row[0], ((Number) row[1]).longValue()));
                }
            }
            return trends;
        } catch (Exception e) {
            logger.error("Failed to fetch trending searches", e);
            return Collections.emptyList();
        }
    }

    /**
     * Retrieve search suggestions based on the user's query prefix.
     */
    public List<SearchSuggestionDto> getSuggestions(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            String normalizedPrefix = prefix.trim().toLowerCase().replaceAll("\\s+", " ");
            List<Object[]> rawSuggestions = searchHistoryRepository.findSuggestionsByPrefix(normalizedPrefix, PageRequest.of(0, 5));
            List<SearchSuggestionDto> suggestions = new ArrayList<>();
            for (Object[] row : rawSuggestions) {
                if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                    suggestions.add(new SearchSuggestionDto((String) row[0], ((Number) row[1]).longValue()));
                }
            }
            return suggestions;
        } catch (Exception e) {
            logger.error("Failed to fetch search suggestions for prefix: {}", prefix, e);
            return Collections.emptyList();
        }
    }
}
