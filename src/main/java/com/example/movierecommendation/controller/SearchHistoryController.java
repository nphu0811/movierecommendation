package com.example.movierecommendation.controller;

import com.example.movierecommendation.dto.SearchClickRequestDto;
import com.example.movierecommendation.dto.SearchSuggestionDto;
import com.example.movierecommendation.dto.SearchTrendDto;
import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.service.SearchHistoryService;
import com.example.movierecommendation.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class SearchHistoryController {

    @Autowired
    private SearchHistoryService searchHistoryService;

    @Autowired
    private UserService userService;

    /**
     * Endpoint to fetch trending search keywords.
     * Accessible by guests and logged-in users.
     */
    @GetMapping("/api/search/trending")
    public ResponseEntity<List<SearchTrendDto>> getTrending(
            @RequestParam(name = "range", defaultValue = "24h") String range) {
        List<SearchTrendDto> trends = searchHistoryService.getTrendingSearches(range);
        return ResponseEntity.ok(trends);
    }

    /**
     * Endpoint to fetch keyword suggestions from search history by prefix.
     * Keeps suggestions separated from movie title autocomplete.
     */
    @GetMapping("/api/search/suggestions")
    public ResponseEntity<List<SearchSuggestionDto>> getSuggestions(
            @RequestParam(name = "prefix") String prefix) {
        List<SearchSuggestionDto> suggestions = searchHistoryService.getSuggestions(prefix);
        return ResponseEntity.ok(suggestions);
    }

    /**
     * Endpoint to track search-to-detail clicks.
     * Validates user or session ownership and prevents tampering.
     */
    @PostMapping("/api/search-history/{searchId}/click")
    public ResponseEntity<Map<String, Object>> trackClick(
            @PathVariable("searchId") Long searchId,
            @RequestBody SearchClickRequestDto clickRequest,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpSession session) {

        User currentUser = null;
        if (userDetails != null) {
            currentUser = userService.getCurrentUser(userDetails.getUsername());
        }
        String sessionId = session.getId();

        boolean success = searchHistoryService.markClickedMovie(
                searchId,
                clickRequest.getMovieId(),
                currentUser,
                sessionId
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);

        if (success) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
}
