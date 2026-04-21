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
import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.util.HashMap;

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
                                 @RequestParam(name = "verificationCode") String verificationCode,
                                 RedirectAttributes redirect) {
        try {
            User user = userService.getCurrentUser(userDetails.getUsername());
            // Delegate validation to service layer
            userService.changePasswordWithVerification(user.getUserId(), currentPassword, newPassword, verificationCode);
            redirect.addFlashAttribute("success", "Password changed successfully!");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Failed to change password");
        }
        return "redirect:/user/profile";
    }

    @PostMapping("/email/send-code")
    @ResponseBody
    public ResponseEntity<?> sendEmailCode(@AuthenticationPrincipal UserDetails userDetails) {
        Map<String, String> response = new HashMap<>();
        try {
            User user = userService.getCurrentUser(userDetails.getUsername());
            userService.sendEmailVerification(user.getUserId());
            response.put("message", "Đã gửi mã xác thực đến " + user.getEmail());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/email/confirm")
    public String confirmEmail(@AuthenticationPrincipal UserDetails userDetails,
                               @RequestParam("code") String code,
                               RedirectAttributes redirect) {
        try {
            User user = userService.getCurrentUser(userDetails.getUsername());
            userService.confirmEmail(user.getUserId(), code);
            redirect.addFlashAttribute("success", "Email đã được xác thực!");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/user/profile";
    }

    @PostMapping("/profile/password/send-code")
    @ResponseBody
    public ResponseEntity<?> sendPasswordChangeCode(@AuthenticationPrincipal UserDetails userDetails) {
        Map<String, String> response = new HashMap<>();
        try {
            User user = userService.getCurrentUser(userDetails.getUsername());
            userService.sendPasswordChangeCode(user.getUserId());
            response.put("message", "Mã xác thực đổi mật khẩu đã được gửi tới email của bạn.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
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

        java.util.concurrent.ExecutorService exec = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Future<java.util.List<com.example.movierecommendation.entity.Movie>> recFuture = exec.submit(() ->
            recommendationService.getPersonalizedRecommendations(user.getUserId()));
        java.util.concurrent.Future<java.util.List<com.example.movierecommendation.entity.Movie>> genreFuture = exec.submit(() ->
            recommendationService.getGenreBasedRecommendations(user.getUserId()));

        try {
            model.addAttribute("recommendations", recFuture.get(5, java.util.concurrent.TimeUnit.SECONDS));
            model.addAttribute("genrePicks", genreFuture.get(3, java.util.concurrent.TimeUnit.SECONDS));
        } catch (Exception e) {
            model.addAttribute("recommendations",
                recommendationService.getTrendingMoviesForUser(user.getUserId()));
            model.addAttribute("genrePicks", java.util.Collections.emptyList());
        } finally {
            exec.shutdown();
        }

        model.addAttribute("trending", recommendationService.getTrendingMoviesForUser(user.getUserId()));
        return "user/recommendations";
    }
}
