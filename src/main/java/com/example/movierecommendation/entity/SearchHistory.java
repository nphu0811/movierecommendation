package com.example.movierecommendation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "search_history")
public class SearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "search_id")
    private Long searchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_search_history_user"))
    private User user;

    @Column(name = "search_query", nullable = false, length = 255)
    private String searchQuery;

    @Column(name = "normalized_query", nullable = false, length = 255)
    private String normalizedQuery;

    @Column(name = "result_count")
    private Integer resultCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clicked_movie_id", foreignKey = @ForeignKey(name = "fk_search_history_clicked_movie"))
    private Movie clickedMovie;

    @Column(name = "clicked_at")
    private LocalDateTime clickedAt;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "search_source", length = 50)
    private String searchSource;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public SearchHistory() {}

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    // --- Getters and Setters ---

    public Long getSearchId() { return searchId; }
    public void setSearchId(Long searchId) { this.searchId = searchId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getSearchQuery() { return searchQuery; }
    public void setSearchQuery(String searchQuery) { this.searchQuery = searchQuery; }

    public String getNormalizedQuery() { return normalizedQuery; }
    public void setNormalizedQuery(String normalizedQuery) { this.normalizedQuery = normalizedQuery; }

    public Integer getResultCount() { return resultCount; }
    public void setResultCount(Integer resultCount) { this.resultCount = resultCount; }

    public Movie getClickedMovie() { return clickedMovie; }
    public void setClickedMovie(Movie clickedMovie) { this.clickedMovie = clickedMovie; }

    public LocalDateTime getClickedAt() { return clickedAt; }
    public void setClickedAt(LocalDateTime clickedAt) { this.clickedAt = clickedAt; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getSearchSource() { return searchSource; }
    public void setSearchSource(String searchSource) { this.searchSource = searchSource; }

    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
