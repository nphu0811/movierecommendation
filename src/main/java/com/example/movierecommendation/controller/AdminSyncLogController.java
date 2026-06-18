package com.example.movierecommendation.controller;

import com.example.movierecommendation.entity.ApiSyncLog;
import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.repository.ApiSyncLogRepository;
import com.example.movierecommendation.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminSyncLogController {

    @Autowired
    private ApiSyncLogRepository apiSyncLogRepository;

    @Autowired
    private UserService userService;

    @GetMapping("/admin/sync-logs")
    public String listSyncLogs(@RequestParam(name = "page", defaultValue = "0") int page,
                               @RequestParam(name = "size", defaultValue = "10") int size,
                               @AuthenticationPrincipal UserDetails userDetails,
                               Model model) {
        if (userDetails == null) {
            return "redirect:/auth/login";
        }
        User user = userService.getCurrentUser(userDetails.getUsername());
        if (!"ADMIN".equals(user.getRole())) {
            return "redirect:/";
        }
        model.addAttribute("currentUser", user);

        Page<ApiSyncLog> syncLogsPage = apiSyncLogRepository.findAll(
                PageRequest.of(page, size, Sort.by("startedAt").descending())
        );
        model.addAttribute("syncLogsPage", syncLogsPage);
        return "admin/sync-logs";
    }
}
