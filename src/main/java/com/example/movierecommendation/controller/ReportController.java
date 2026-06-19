package com.example.movierecommendation.controller;

import com.example.movierecommendation.entity.*;
import com.example.movierecommendation.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
public class ReportController {

    @Autowired
    private MovieReportService reportService;

    @Autowired
    private UserService userService;

    @PostMapping("/api/movies/{id}/report")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitReport(
            @PathVariable("id") Integer id,
            @RequestParam("reportType") String reportTypeStr,
            @RequestParam(value = "message", required = false) String message,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userService.getCurrentUser(userDetails.getUsername());
        boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
        MovieReportType reportType;
        try {
            reportType = MovieReportType.valueOf(reportTypeStr);
        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", isVi ? "Loại báo cáo không hợp lệ." : "Invalid report type.");
            return ResponseEntity.badRequest().body(err);
        }

        try {
            MovieReport report = reportService.submitReport(user, id, reportType, message);
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("reportId", report.getReportId());
            result.put("message", isVi ? "Báo cáo của bạn đã được gửi và đang chờ xử lý." : "Your report has been sent and is pending review.");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }
}
