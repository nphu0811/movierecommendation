package com.example.movierecommendation.controller;

import com.example.movierecommendation.entity.*;
import com.example.movierecommendation.service.*;
import java.util.List;
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
    @Autowired
    private com.example.movierecommendation.repository.UserPreferenceRepository userPreferenceRepository;
    @Autowired
    private com.example.movierecommendation.repository.SearchHistoryRepository searchHistoryRepository;
    @Autowired
    private com.example.movierecommendation.service.SearchHistoryService searchHistoryService;

    @Autowired
    private MovieService movieService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal UserDetails userDetails,
                          @RequestParam(name = "tab", required = false, defaultValue = "edit-profile") String activeTab,
                          Model model) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        model.addAttribute("currentUser", user);
        
        List<WatchHistory> history = interactionService.getWatchHistory(user.getUserId());
        List<Watchlist> watchlist = interactionService.getWatchlist(user.getUserId());
        
        model.addAttribute("watchHistory", history);
        model.addAttribute("watchlist", watchlist);
        
        // Progress Map for Watchlist items
        Map<Integer, Double> progressMap = new HashMap<>();
        for (Watchlist wl : watchlist) {
            interactionService.getWatchHistoryEntry(user.getUserId(), wl.getMovie().getMovieId())
                .ifPresent(wh -> progressMap.put(wl.getMovie().getMovieId(), wh.getProgress()));
        }
        model.addAttribute("progressMap", progressMap);

        // Retrieve Search History
        List<SearchHistory> searchHist = searchHistoryRepository.findByUserUserIdOrderByCreatedAtDesc(user.getUserId());
        model.addAttribute("searchHistory", searchHist);
        
        model.addAttribute("activeTab", activeTab);
        
        return "user/profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@AuthenticationPrincipal UserDetails userDetails,
                                @RequestParam(name = "username") String username,
                                @RequestParam(name = "email") String email,
                                jakarta.servlet.http.HttpServletRequest request,
                                RedirectAttributes redirect) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        try {
            boolean emailChanged = !user.getEmail().equalsIgnoreCase(email);
            userService.updateProfile(user.getUserId(), username, email);
            
            if (emailChanged) {
                // Programmatically update the authentication in security context
                UserDetails newUserDetails = userDetailsService.loadUserByUsername(email);
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth = 
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        newUserDetails, null, newUserDetails.getAuthorities());
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                
                request.getSession().setAttribute(
                    org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    org.springframework.security.core.context.SecurityContextHolder.getContext()
                );
            }
            
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
                                 @RequestParam(name = "confirmPassword") String confirmPassword,
                                 RedirectAttributes redirect) {
        boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
        try {
            User user = userService.getCurrentUser(userDetails.getUsername());
            if (!newPassword.equals(confirmPassword)) {
                throw new IllegalArgumentException(isVi ? "Mật khẩu xác nhận không khớp." : "Confirm password does not match.");
            }
            userService.changePassword(user.getUserId(), currentPassword, newPassword);
            redirect.addFlashAttribute("success", isVi ? "Thay đổi mật khẩu thành công!" : "Password changed successfully!");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("currentPasswordVal", currentPassword);
            redirect.addFlashAttribute("newPasswordVal", newPassword);
            redirect.addFlashAttribute("confirmPasswordVal", confirmPassword);

            String msg = e.getMessage();
            String lowerMsg = msg.toLowerCase();
            if (lowerMsg.contains("sai mật khẩu") || lowerMsg.contains("incorrect") || lowerMsg.equals("mật khẩu hiện tại không chính xác") || lowerMsg.equals("current password is incorrect")) {
                redirect.addFlashAttribute("currentPasswordError", msg);
            } else if (lowerMsg.contains("xác nhận") || lowerMsg.contains("confirm")) {
                redirect.addFlashAttribute("confirmPasswordError", msg);
            } else {
                redirect.addFlashAttribute("newPasswordError", msg);
            }
        } catch (Exception e) {
            redirect.addFlashAttribute("currentPasswordVal", currentPassword);
            redirect.addFlashAttribute("newPasswordVal", newPassword);
            redirect.addFlashAttribute("confirmPasswordVal", confirmPassword);
            redirect.addFlashAttribute("error", isVi ? "Không thể thay đổi mật khẩu" : "Failed to change password");
        }
        return "redirect:/user/profile?tab=change-password";
    }

    @PostMapping("/profile/password/reset-send-code")
    @ResponseBody
    public ResponseEntity<?> resetSendCode(@AuthenticationPrincipal UserDetails userDetails,
                                           @RequestParam("email") String email) {
        Map<String, Object> response = new HashMap<>();
        boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
        try {
            User user = userService.getCurrentUser(userDetails.getUsername());
            if (!user.getEmail().equalsIgnoreCase(email)) {
                throw new IllegalArgumentException(isVi ? "Email không khớp với tài khoản hiện tại." : "Email does not match current account.");
            }
            String masked = userService.sendPasswordReset(email);
            response.put("success", true);
            response.put("message", isVi ? "Mã xác thực đã được gửi tới " + masked : "Verification code sent to " + masked);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/profile/password/reset-verify-code")
    @ResponseBody
    public ResponseEntity<?> resetVerifyCode(@AuthenticationPrincipal UserDetails userDetails,
                                             @RequestParam("email") String email,
                                             @RequestParam("code") String code) {
        Map<String, Object> response = new HashMap<>();
        boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
        try {
            User user = userService.getCurrentUser(userDetails.getUsername());
            if (!user.getEmail().equalsIgnoreCase(email)) {
                throw new IllegalArgumentException(isVi ? "Email không khớp với tài khoản hiện tại." : "Email does not match current account.");
            }
            userService.verifyResetCodeOnly(email, code);
            response.put("success", true);
            response.put("message", isVi ? "Xác thực thành công!" : "Verification successful!");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/profile/password/reset-submit")
    @ResponseBody
    public ResponseEntity<?> resetSubmit(@AuthenticationPrincipal UserDetails userDetails,
                                         @RequestParam("email") String email,
                                         @RequestParam("code") String code,
                                         @RequestParam("newPassword") String newPassword,
                                         @RequestParam("confirmPassword") String confirmPassword) {
        Map<String, Object> response = new HashMap<>();
        boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
        if (!newPassword.equals(confirmPassword)) {
            response.put("success", false);
            response.put("error", isVi ? "Mật khẩu xác nhận không khớp." : "Confirm password does not match.");
            return ResponseEntity.badRequest().body(response);
        }
        try {
            User user = userService.getCurrentUser(userDetails.getUsername());
            if (!user.getEmail().equalsIgnoreCase(email)) {
                throw new IllegalArgumentException(isVi ? "Email không khớp với tài khoản hiện tại." : "Email does not match current account.");
            }
            userService.resetPassword(email, code, newPassword);
            response.put("success", true);
            response.put("message", isVi ? "Đổi mật khẩu thành công!" : "Password reset successful!");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/email/send-code")
    @ResponseBody
    public ResponseEntity<?> sendEmailCode(@AuthenticationPrincipal UserDetails userDetails) {
        Map<String, String> response = new HashMap<>();
        try {
            User user = userService.getCurrentUser(userDetails.getUsername());
            userService.sendEmailVerification(user.getUserId());
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            response.put("message", isVi ? "Đã gửi mã xác thực đến " + user.getEmail() : "Verification code sent to " + user.getEmail());
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
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            redirect.addFlashAttribute("success", isVi ? "Email đã được xác thực!" : "Email verified successfully!");
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
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            response.put("message", isVi ? "Mã xác thực đổi mật khẩu đã được gửi tới email của bạn." : "Verification code for password change has been sent to your email.");
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
        
        List<Watchlist> watchlist = interactionService.getWatchlist(user.getUserId());
        model.addAttribute("watchlist", watchlist);
        
        Map<Integer, Double> progressMap = new HashMap<>();
        for (Watchlist wl : watchlist) {
            interactionService.getWatchHistoryEntry(user.getUserId(), wl.getMovie().getMovieId())
                .ifPresent(wh -> progressMap.put(wl.getMovie().getMovieId(), wh.getProgress()));
        }
        model.addAttribute("progressMap", progressMap);
        
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



    @DeleteMapping("/api/watch-history/{historyId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteWatchHistory(
            @PathVariable("historyId") Integer historyId,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).build();
        User user = userService.getCurrentUser(userDetails.getUsername());
        
        interactionService.deleteWatchHistoryEntry(user.getUserId(), historyId);
        
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/api/watch-history/clear")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearWatchHistory(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).build();
        User user = userService.getCurrentUser(userDetails.getUsername());
        
        interactionService.clearWatchHistory(user.getUserId());
        
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/api/search-history/{searchId}")
    @ResponseBody
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> deleteSearchHistory(
            @PathVariable("searchId") Long searchId,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).build();
        User user = userService.getCurrentUser(userDetails.getUsername());
        
        searchHistoryRepository.deleteBySearchIdAndUserUserId(searchId, user.getUserId());
        
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/api/search-history/clear")
    @ResponseBody
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> clearSearchHistory(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).build();
        User user = userService.getCurrentUser(userDetails.getUsername());
        
        searchHistoryRepository.deleteByUserUserId(user.getUserId());
        
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        return ResponseEntity.ok(res);
    }
}
