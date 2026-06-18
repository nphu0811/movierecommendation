package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.*;
import com.example.movierecommendation.repository.MovieReportRepository;
import com.example.movierecommendation.repository.MovieRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class MovieReportService {

    @Autowired
    private MovieReportRepository reportRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Transactional
    public MovieReport submitReport(User user, Integer movieId, MovieReportType reportType, String message) {
        if (movieId == null || reportType == null) {
            throw new IllegalArgumentException("Movie and Report Type are required.");
        }
        if (reportType == MovieReportType.OTHER && (message == null || message.trim().isEmpty())) {
            throw new IllegalArgumentException("Description is required for OTHER report type.");
        }

        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new IllegalArgumentException("Movie not found with ID: " + movieId));

        MovieReport report = new MovieReport();
        report.setUser(user);
        report.setMovie(movie);
        report.setReportType(reportType);
        // Sanitize message to prevent XSS
        String cleanMsg = message != null ? org.springframework.web.util.HtmlUtils.htmlEscape(message.trim()) : "";
        report.setMessage(cleanMsg);
        report.setStatus(MovieReportStatus.NEW);

        return reportRepository.save(report);
    }

    @Transactional
    public MovieReport updateReportStatus(Integer reportId, MovieReportStatus status, String adminNote) {
        MovieReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found with ID: " + reportId));

        report.setStatus(status);
        report.setAdminNote(adminNote);
        if (status == MovieReportStatus.RESOLVED || status == MovieReportStatus.REJECTED) {
            report.setResolvedAt(LocalDateTime.now());
        }

        return reportRepository.save(report);
    }

    public Page<MovieReport> getReports(Pageable pageable) {
        return reportRepository.findAll(pageable);
    }

    public Page<MovieReport> getReportsByStatus(MovieReportStatus status, Pageable pageable) {
        return reportRepository.findByStatus(status, pageable);
    }

    public Optional<MovieReport> findById(Integer id) {
        return reportRepository.findById(id);
    }

    public long countAllReports() {
        return reportRepository.count();
    }
}
