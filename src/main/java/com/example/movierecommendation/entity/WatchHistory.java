package com.example.movierecommendation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "watch_history", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "movie_id"})
})
public class WatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Integer historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "watch_duration")
    private Integer watchDuration; // in seconds

    @Column(name = "progress")
    private Double progress; // 0-100

    @Column(name = "watched_at")
    private LocalDateTime watchedAt;

    public WatchHistory() {}

    @PrePersist
    protected void onCreate() {
        if (watchedAt == null) watchedAt = LocalDateTime.now();
    }

    public Integer getHistoryId() { return historyId; }
    public void setHistoryId(Integer historyId) { this.historyId = historyId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Movie getMovie() { return movie; }
    public void setMovie(Movie movie) { this.movie = movie; }

    public Integer getWatchDuration() { return watchDuration; }
    public void setWatchDuration(Integer watchDuration) { this.watchDuration = watchDuration; }

    public Double getProgress() { return progress; }
    public void setProgress(Double progress) { this.progress = progress; }

    public LocalDateTime getWatchedAt() { return watchedAt; }
    public void setWatchedAt(LocalDateTime watchedAt) { this.watchedAt = watchedAt; }

    /**
     * Trả về thời lượng xem định dạng "X phút Y giây"
     */
    public String getFormattedDuration() {
        if (watchDuration == null || watchDuration == 0) return "0 giây";
        int minutes = watchDuration / 60;
        int seconds = watchDuration % 60;
        if (minutes == 0) return seconds + " giây";
        return minutes + " phút " + seconds + " giây";
    }
}
