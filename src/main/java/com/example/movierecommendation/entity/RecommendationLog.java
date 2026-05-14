package com.example.movierecommendation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "recommendation_logs")
public class RecommendationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Integer logId;

    @Column(name = "algorithm_name", nullable = false, length = 100)
    private String algorithmName;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "total_users")
    private Integer totalUsers = 0;

    @Column(name = "total_movies")
    private Integer totalMovies = 0;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public RecommendationLog() {}

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) generatedAt = LocalDateTime.now();
        if (totalUsers == null) totalUsers = 0;
        if (totalMovies == null) totalMovies = 0;
    }

    public Integer getLogId() { return logId; }
    public void setLogId(Integer logId) { this.logId = logId; }
    public String getAlgorithmName() { return algorithmName; }
    public void setAlgorithmName(String algorithmName) { this.algorithmName = algorithmName; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public Integer getTotalUsers() { return totalUsers; }
    public void setTotalUsers(Integer totalUsers) { this.totalUsers = totalUsers; }
    public Integer getTotalMovies() { return totalMovies; }
    public void setTotalMovies(Integer totalMovies) { this.totalMovies = totalMovies; }
    public Integer getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(Integer executionTimeMs) { this.executionTimeMs = executionTimeMs; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
