package com.example.movierecommendation.controller;

import com.example.movierecommendation.dto.RegisterRequest;
import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.entity.UserPreference;
import com.example.movierecommendation.service.*;
import com.example.movierecommendation.service.EmailNotVerifiedException;
import com.example.movierecommendation.repository.UserPreferenceRepository;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private MovieService movieService;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @GetMapping("/login")
    public String loginPage(@RequestParam(name = "error", required = false) String error,
                            @RequestParam(name = "logout", required = false) String logout,
                            Model model) {
        if ("locked".equals(error)) {
            model.addAttribute("error",
                "🔒 Your account has been locked. Please contact the administrator.");
        } else if (error != null) {
            model.addAttribute("error", "Invalid email or password.");
        }
        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully.");
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterRequest request,
                           BindingResult result,
                           RedirectAttributes redirect,
                           Model model) {
        if (result.hasErrors()) return "auth/register";
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            model.addAttribute("error", "Passwords do not match");
            return "auth/register";
        }
        try {
            userService.register(request);
            redirect.addFlashAttribute("email", request.getEmail());
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            redirect.addFlashAttribute("success", isVi 
                ? "Đăng ký thành công! Mã xác thực đã được gửi tới email " + request.getEmail()
                : "Registration successful! Verification code sent to " + request.getEmail());
            return "redirect:/auth/verify-email?email=" + java.net.URLEncoder.encode(request.getEmail(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "auth/register";
        }
    }

    @GetMapping("/verify-email")
    public String verifyEmailPage(@RequestParam(name = "email", required = false) String email,
                                  @ModelAttribute("email") String flashEmail,
                                  Model model) {
        String finalEmail = (email != null && !email.isEmpty()) ? email : flashEmail;
        model.addAttribute("email", finalEmail);
        try {
            User user = userService.getCurrentUser(finalEmail);
            model.addAttribute("hasPreviousEmail", user.getPreviousEmail() != null);
        } catch (Exception e) {
            model.addAttribute("hasPreviousEmail", false);
        }
        return "auth/verify-email";
    }

    @PostMapping("/verify-email")
    public String verifyEmail(@RequestParam("email") String email,
                              @RequestParam("code") String code,
                              RedirectAttributes redirect,
                              Model model,
                              HttpServletRequest request) {
        try {
            User user = userService.getCurrentUser(email);
            userService.confirmEmail(user.getUserId(), code);

            // Programmatically login the user
            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth = 
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            
            // Set in session context
            request.getSession().setAttribute(
                org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                org.springframework.security.core.context.SecurityContextHolder.getContext()
            );

            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            redirect.addFlashAttribute("success", isVi 
                ? "Xác thực email và đăng nhập thành công!" 
                : "Email verified and logged in successfully!");
            return "redirect:/auth/preferences";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            return "auth/verify-email";
        }
    }

    @PostMapping("/verify-email/resend")
    @ResponseBody
    public Map<String, Object> resendCode(@RequestParam("email") String email) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = userService.getCurrentUser(email);
            userService.sendEmailVerification(user.getUserId());
            response.put("success", true);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }

    @GetMapping("/preferences")
    public String preferencesPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/auth/login";
        }
        User user = userService.getCurrentUser(userDetails.getUsername());
        model.addAttribute("currentUser", user);
        model.addAttribute("allGenres", movieService.getAllGenres());
        return "auth/preferences";
    }

    @PostMapping("/preferences")
    @ResponseBody
    public Map<String, Object> savePreferences(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(name = "preferredGenres", required = false) List<String> preferredGenres,
            @RequestParam(name = "dislikedGenres", required = false) List<String> dislikedGenres,
            @RequestParam(name = "preferNewReleases", defaultValue = "false") Boolean preferNewReleases,
            @RequestParam(name = "preferTopRated", defaultValue = "false") Boolean preferTopRated) {
        
        Map<String, Object> response = new HashMap<>();
        if (userDetails == null) {
            response.put("success", false);
            response.put("message", "Unauthorized");
            return response;
        }

        try {
            User user = userService.getCurrentUser(userDetails.getUsername());
            
            UserPreference pref = userPreferenceRepository.findByUserUserId(user.getUserId())
                .orElseGet(() -> {
                    UserPreference p = new UserPreference();
                    p.setUser(user);
                    return p;
                });
            pref.setPreferredGenres(preferredGenres != null ? String.join(",", preferredGenres) : "");
            pref.setDislikedGenres(dislikedGenres != null ? String.join(",", dislikedGenres) : "");
            pref.setPreferNewReleases(preferNewReleases);
            pref.setPreferTopRated(preferTopRated);
            userPreferenceRepository.save(pref);

            recommendationService.evictRecommendationsCache(user.getUserId());

            // Precompute hybrid/AI recommendations in the background so they are instantly loaded next time
            recommendationService.computeAndPersistPersonalizedRecommendations(user.getUserId());

            response.put("success", true);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password/send-code")
    public String sendResetCode(@RequestParam("email") String email,
                                RedirectAttributes redirect) {
        try {
            String masked = userService.sendPasswordReset(email);
            redirect.addFlashAttribute("email", email);
            redirect.addFlashAttribute("maskedEmail", masked);
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            redirect.addFlashAttribute("success", isVi ? "Đã gửi mã xác thực tới " + masked : "Verification code sent to " + masked);
            return "redirect:/auth/forgot-password/verify?email=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8);
        } catch (EmailNotVerifiedException e) {
            redirect.addFlashAttribute("email", email);
            redirect.addFlashAttribute("unverifiedEmail", email);
            redirect.addFlashAttribute("showCancelOption", true);
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            redirect.addFlashAttribute("error", isVi 
                ? "Email này chưa được xác thực. Bạn phải xác thực email trước khi đặt lại mật khẩu."
                : "This email is not verified. You must verify your email before resetting the password.");
            return "redirect:/auth/forgot-password";
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/auth/forgot-password";
        }
    }

    @PostMapping("/forgot-password/cancel-email-change")
    public String cancelEmailChange(@RequestParam("email") String email,
                                    RedirectAttributes redirect) {
        try {
            String originalEmail = userService.revertEmailChange(email);
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            redirect.addFlashAttribute("success", isVi 
                ? "Đã huỷ thay đổi email. Email của bạn đã được khôi phục về " + originalEmail + " (đã xác thực)."
                : "Email change cancelled. Your email has been reverted to " + originalEmail + " (verified).");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/auth/forgot-password";
    }

    @GetMapping("/forgot-password/verify")
    public String verifyResetCodePage(@RequestParam(name = "email", required = false) String email,
                                      @ModelAttribute("email") String flashEmail,
                                      Model model) {
        String finalEmail = (email != null && !email.isEmpty()) ? email : flashEmail;
        model.addAttribute("email", finalEmail);
        return "auth/verify-reset-code";
    }

    @PostMapping("/forgot-password/verify")
    public String verifyResetCode(@RequestParam("email") String email,
                                  @RequestParam("code") String code,
                                  RedirectAttributes redirect,
                                  Model model) {
        try {
            userService.verifyResetCodeOnly(email, code);
            redirect.addFlashAttribute("email", email);
            redirect.addFlashAttribute("code", code);
            return "redirect:/auth/reset-password";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            return "auth/verify-reset-code";
        }
    }

    @PostMapping("/forgot-password/resend")
    @ResponseBody
    public Map<String, Object> resendResetCode(@RequestParam("email") String email) {
        Map<String, Object> response = new HashMap<>();
        try {
            userService.sendPasswordReset(email);
            response.put("success", true);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(name = "email", required = false) String email,
                                    @RequestParam(name = "code", required = false) String code,
                                    @ModelAttribute("email") String flashEmail,
                                    @ModelAttribute("code") String flashCode,
                                    Model model,
                                    RedirectAttributes redirect) {
        String finalEmail = (email != null && !email.isEmpty()) ? email : flashEmail;
        String finalCode = (code != null && !code.isEmpty()) ? code : flashCode;
        if (finalEmail == null || finalEmail.isEmpty() || finalCode == null || finalCode.isEmpty()) {
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            redirect.addFlashAttribute("error", isVi ? "Yêu cầu đặt lại mật khẩu không hợp lệ." : "Invalid password reset request.");
            return "redirect:/auth/forgot-password";
        }
        model.addAttribute("email", finalEmail);
        model.addAttribute("code", finalCode);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam("email") String email,
                                @RequestParam("code") String code,
                                @RequestParam("newPassword") String newPassword,
                                @RequestParam("confirmPassword") String confirmPassword,
                                RedirectAttributes redirect) {
        boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
        if (!newPassword.equals(confirmPassword)) {
            redirect.addFlashAttribute("error", isVi ? "Mật khẩu xác nhận không khớp." : "Confirm password does not match.");
            redirect.addFlashAttribute("email", email);
            redirect.addFlashAttribute("code", code);
            return "redirect:/auth/reset-password";
        }
        try {
            userService.resetPassword(email, code, newPassword);
            redirect.addFlashAttribute("success", isVi ? "Đổi mật khẩu thành công. Hãy đăng nhập lại." : "Password reset successful. Please log in again.");
            return "redirect:/auth/login";
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            redirect.addFlashAttribute("email", email);
            redirect.addFlashAttribute("code", code);
            return "redirect:/auth/reset-password";
        }
    }
}
