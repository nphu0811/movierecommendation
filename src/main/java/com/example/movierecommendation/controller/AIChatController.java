package com.example.movierecommendation.controller;

import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.service.AIChatService;
import com.example.movierecommendation.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
public class AIChatController {

    @Autowired
    private AIChatService aiChatService;

    @Autowired
    private UserService userService;

    @GetMapping("/ai-chat")
    public String aiChatPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails != null) {
            User user = userService.getCurrentUser(userDetails.getUsername());
            model.addAttribute("currentUser", user);
        }
        return "ai-chat";
    }

    @PostMapping("/api/ai-chat/recommend")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> recommendMovies(
            @RequestBody Map<String, String> requestBody,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        String message = requestBody.get("message");
        if (message == null || message.trim().isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Message cannot be empty");
            return ResponseEntity.badRequest().body(err);
        }

        User currentUser = null;
        if (userDetails != null) {
            currentUser = userService.getCurrentUser(userDetails.getUsername());
        }

        Map<String, Object> recommendation = aiChatService.recommendMovies(currentUser, message.trim());
        return ResponseEntity.ok(recommendation);
    }
}
