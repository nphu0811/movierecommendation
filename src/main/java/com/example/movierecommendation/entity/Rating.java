package com.example.movierecommendation.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ratings", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "movie_id"})
})
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rating_id")
    private Integer ratingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "rating", nullable = false, precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(name = "rated_at")
    private LocalDateTime ratedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public Rating() {}

    @PrePersist
    protected void onCreate() {
        if (ratedAt == null) ratedAt = LocalDateTime.now();
    }

    public Integer getRatingId() { return ratingId; }
    public void setRatingId(Integer ratingId) { this.ratingId = ratingId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Movie getMovie() { return movie; }
    public void setMovie(Movie movie) { this.movie = movie; }

    public Double getRating() { return rating != null ? rating.doubleValue() : null; }
    public void setRating(Double rating) {
        this.rating = rating != null ? BigDecimal.valueOf(rating) : null;
    }
    public void setRating(Integer rating) {
        this.rating = rating != null ? BigDecimal.valueOf(rating.doubleValue()) : null;
    }
    public BigDecimal getRatingValue() { return rating; }
    public void setRatingValue(BigDecimal rating) { this.rating = rating; }

    public LocalDateTime getRatedAt() { return ratedAt; }
    public void setRatedAt(LocalDateTime ratedAt) { this.ratedAt = ratedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
