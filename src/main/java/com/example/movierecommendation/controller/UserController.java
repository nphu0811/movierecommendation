package com.example.movierecommendation.controller;

import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private InteractionService interactionService;
    @Autowired
    private RecommendationService recommendationService;

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        model.addAttribute("currentUser", user);
        model.addAttribute("watchHistory", interactionService.getWatchHistory(user.getUserId()));
        model.addAttribute("watchlist", interactionService.getWatchlist(user.getUserId()));
        return "user/profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@AuthenticationPrincipal UserDetails userDetails,
                                @RequestParam(name = "username") String username,
                                @RequestParam(name = "email") String email,
                                RedirectAttributes redirect) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        try {
            userService.updateProfile(user.getUserId(), username, email);
            redirect.addFlashAttribute("success", "Profile updated successfully");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/user/profile";
    }

    @PostMapping("/profile/change-password")
    public String changePassword(@AuthenticationPrincipal UserDetails userDetails,
                                 @RequestParam(name = "currentPassword") String currentPassword,
                                 @RequestParam(name = "newPassword") String newPassword,
                                 RedirectAttributes redirect) {
        try {
            User user = userService.getCurrentUser(userDetails.getUsername());
            // Delegate validation to service layer
            userService.changePasswordWithVerification(user.getUserId(), currentPassword, newPassword);
            redirect.addFlashAttribute("success", "Password changed successfully!");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Failed to change password");
        }
        return "redirect:/user/profile";
    }

    @GetMapping("/watchlist")
    public String watchlist(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        model.addAttribute("currentUser", user);
        model.addAttribute("watchlist", interactionService.getWatchlist(user.getUserId()));
        return "user/watchlist";
    }

    @GetMapping("/history")
    public String history(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        model.addAttribute("currentUser", user);
        model.addAttribute("watchHistory", interactionService.getWatchHistory(user.getUserId()));
        return "user/history";
    }

    @GetMapping("/recommendations")
    public String recommendations(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        model.addAttribute("currentUser", user);
        model.addAttribute("recommendations",
                recommendationService.getPersonalizedRecommendations(user.getUserId()));
        model.addAttribute("genrePicks",
                recommendationService.getGenreBasedRecommendations(user.getUserId()));
        model.addAttribute("trending", recommendationService.getTrendingMovies());
        return "user/recommendations";
    }
}
