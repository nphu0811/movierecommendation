package com.example.movierecommendation.controller;

import com.example.movierecommendation.entity.MovieReport;
import com.example.movierecommendation.entity.MovieReportStatus;
import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.service.MovieReportService;
import com.example.movierecommendation.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/reports")
public class AdminReportController {

    @Autowired
    private MovieReportService reportService;

    @Autowired
    private UserService userService;

    private void addCurrentUser(UserDetails ud, Model model) {
        if (ud != null) {
            User u = userService.getCurrentUser(ud.getUsername());
            model.addAttribute("currentUser", u);
        }
    }

    @GetMapping
    public String listReports(@RequestParam(name = "page", defaultValue = "0") int page,
                              @RequestParam(name = "status", required = false) String statusStr,
                              @AuthenticationPrincipal UserDetails ud,
                              Model model) {
        addCurrentUser(ud, model);
        
        Page<MovieReport> reportPage;
        PageRequest pageRequest = PageRequest.of(page, 15, Sort.by("createdAt").descending());

        if (statusStr != null && !statusStr.trim().isEmpty()) {
            try {
                MovieReportStatus status = MovieReportStatus.valueOf(statusStr);
                reportPage = reportService.getReportsByStatus(status, pageRequest);
                model.addAttribute("selectedStatus", statusStr);
            } catch (IllegalArgumentException e) {
                reportPage = reportService.getReports(pageRequest);
            }
        } else {
            reportPage = reportService.getReports(pageRequest);
        }

        model.addAttribute("reportPage", reportPage);
        model.addAttribute("statuses", MovieReportStatus.values());
        return "admin/reports";
    }

    @PostMapping("/{id}/resolve")
    public String resolveReport(@PathVariable("id") Integer id,
                                @RequestParam("status") String statusStr,
                                @RequestParam(value = "adminNote", required = false) String adminNote,
                                RedirectAttributes redirect) {
        try {
            MovieReportStatus status = MovieReportStatus.valueOf(statusStr);
            reportService.updateReportStatus(id, status, adminNote);
            redirect.addFlashAttribute("success", "Cập nhật trạng thái báo cáo thành công.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/reports";
    }
}
