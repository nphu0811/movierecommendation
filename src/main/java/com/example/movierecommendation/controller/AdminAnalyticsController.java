package com.example.movierecommendation.controller;

import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.repository.EmailVerificationTokenRepository;
import com.example.movierecommendation.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/analytics")
public class AdminAnalyticsController {

    @Autowired
    private SearchHistoryService searchHistoryService;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private UserService userService;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    private void addCurrentUser(UserDetails ud, Model model) {
        if (ud != null) {
            User u = userService.getCurrentUser(ud.getUsername());
            model.addAttribute("currentUser", u);
        }
    }

    @GetMapping
    public String viewAnalytics(@AuthenticationPrincipal UserDetails ud, Model model) {
        addCurrentUser(ud, model);

        // Search Analytics
        model.addAttribute("totalSearches", searchHistoryService.countAllSearches());
        model.addAttribute("avgSearchLatency", searchHistoryService.getAverageSearchLatency());
        model.addAttribute("topSearches", searchHistoryService.getTrendingSearches("30d"));
        model.addAttribute("topClickedMovies", searchHistoryService.getTopClickedMovies(10));
        model.addAttribute("zeroResultQueries", searchHistoryService.getZeroResultQueries(10));

        // Recommendation Analytics
        model.addAttribute("latestLogs", recommendationService.getLatestRecommendationLogs(10));
        model.addAttribute("topRecommended", recommendationService.getTopRecommendedMovies(10));
        model.addAttribute("algorithmDistribution", recommendationService.getAlgorithmDistribution());

        // Email Verification Tokens
        model.addAttribute("latestTokens", emailVerificationTokenRepository.findLatest(PageRequest.of(0, 15)));

        return "admin/analytics";
    }
}
