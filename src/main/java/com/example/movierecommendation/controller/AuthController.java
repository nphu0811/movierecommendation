package com.example.movierecommendation.controller;

import com.example.movierecommendation.dto.RegisterRequest;
import com.example.movierecommendation.service.UserService;
import com.example.movierecommendation.service.VerificationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private VerificationService verificationService;

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
            redirect.addFlashAttribute("success", "Registration successful! Please login.");
            return "redirect:/auth/login";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "auth/register";
        }
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
            redirect.addFlashAttribute("step", "code");
            redirect.addFlashAttribute("maskedEmail", masked);
            redirect.addFlashAttribute("email", email);
            redirect.addFlashAttribute("success", "Đã gửi mã xác thực tới " + masked);
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/auth/forgot-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam("email") String email,
                                @RequestParam("code") String code,
                                @RequestParam("newPassword") String newPassword,
                                @RequestParam("confirmPassword") String confirmPassword,
                                RedirectAttributes redirect) {
        if (!newPassword.equals(confirmPassword)) {
            redirect.addFlashAttribute("error", "Mật khẩu xác nhận không khớp");
            redirect.addFlashAttribute("step", "code");
            redirect.addFlashAttribute("email", email);
            return "redirect:/auth/forgot-password";
        }
        try {
            userService.resetPassword(email, code, newPassword);
            redirect.addFlashAttribute("success", "Đổi mật khẩu thành công. Hãy đăng nhập lại.");
            return "redirect:/auth/login";
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            redirect.addFlashAttribute("step", "code");
            redirect.addFlashAttribute("email", email);
            redirect.addFlashAttribute("maskedEmail", verificationService.maskEmail(email));
            return "redirect:/auth/forgot-password";
        }
    }
}
