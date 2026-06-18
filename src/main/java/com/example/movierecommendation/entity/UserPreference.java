package com.example.movierecommendation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_preferences")
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "preference_id")
    private Integer preferenceId;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "preferred_genres", columnDefinition = "TEXT")
    private String preferredGenres;

    @Column(name = "disliked_genres", columnDefinition = "TEXT")
    private String dislikedGenres;

    @Column(name = "min_rating")
    private Double minRating;

    @Column(name = "prefer_new_releases")
    private Boolean preferNewReleases = false;

    @Column(name = "prefer_top_rated")
    private Boolean preferTopRated = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserPreference() {}

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (preferNewReleases == null) preferNewReleases = false;
        if (preferTopRated == null) preferTopRated = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Integer getPreferenceId() { return preferenceId; }
    public void setPreferenceId(Integer preferenceId) { this.preferenceId = preferenceId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getPreferredGenres() { return preferredGenres; }
    public void setPreferredGenres(String preferredGenres) { this.preferredGenres = preferredGenres; }

    public String getDislikedGenres() { return dislikedGenres; }
    public void setDislikedGenres(String dislikedGenres) { this.dislikedGenres = dislikedGenres; }

    public Double getMinRating() { return minRating; }
    public void setMinRating(Double minRating) { this.minRating = minRating; }

    public Boolean getPreferNewReleases() { return preferNewReleases; }
    public void setPreferNewReleases(Boolean preferNewReleases) { this.preferNewReleases = preferNewReleases; }

    public Boolean getPreferTopRated() { return preferTopRated; }
    public void setPreferTopRated(Boolean preferTopRated) { this.preferTopRated = preferTopRated; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
