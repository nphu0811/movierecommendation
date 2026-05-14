package com.example.movierecommendation.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_recommendations",
       uniqueConstraints = @UniqueConstraint(name = "uq_user_movie_algorithm",
           columnNames = {"user_id", "movie_id", "algorithm_type"}))
public class UserRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_id")
    private Integer recommendationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "score", nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "algorithm_type", length = 50)
    private String algorithmType;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    public UserRecommendation() {}

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) generatedAt = LocalDateTime.now();
    }

    public Integer getRecommendationId() { return recommendationId; }
    public void setRecommendationId(Integer recommendationId) { this.recommendationId = recommendationId; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Movie getMovie() { return movie; }
    public void setMovie(Movie movie) { this.movie = movie; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public String getAlgorithmType() { return algorithmType; }
    public void setAlgorithmType(String algorithmType) { this.algorithmType = algorithmType; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
