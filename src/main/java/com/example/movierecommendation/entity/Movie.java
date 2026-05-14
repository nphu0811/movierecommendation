package com.example.movierecommendation.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "movies", indexes = {
    @Index(name = "idx_movie_title", columnList = "title")
})
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_id")
    private Integer movieId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "trailer_key", length = 50)
    private String trailerKey;

    @Column(name = "backdrop_url")
    private String backdropUrl;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(name = "poster_url", length = 255)
    private String posterUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "rating_count")
    private Integer ratingCount = 0;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "movie_genres",
        joinColumns = @JoinColumn(name = "movie_id"),
        inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private List<Genre> genres;

    @OneToMany(mappedBy = "movie", fetch = FetchType.LAZY)
    private List<Rating> ratings;

    @OneToMany(mappedBy = "movie", fetch = FetchType.LAZY)
    private List<Comment> comments;

    @OneToMany(mappedBy = "movie", fetch = FetchType.LAZY)
    private List<WatchHistory> watchHistories;

    @OneToOne(mappedBy = "movie", fetch = FetchType.LAZY)
    private Link link;

    @OneToMany(mappedBy = "movie", fetch = FetchType.LAZY)
    private List<Tag> tags;

    public Movie() {}

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (averageRating == null) averageRating = BigDecimal.ZERO;
        if (ratingCount == null) ratingCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Integer getMovieId() { return movieId; }
    public void setMovieId(Integer movieId) { this.movieId = movieId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTrailerKey() { return trailerKey; }
    public void setTrailerKey(String trailerKey) { this.trailerKey = trailerKey; }
    public String getBackdropUrl() { return backdropUrl; }
    public void setBackdropUrl(String backdropUrl) { this.backdropUrl = backdropUrl; }

    public Integer getReleaseYear() { return releaseYear; }
    public void setReleaseYear(Integer releaseYear) { this.releaseYear = releaseYear; }

    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public List<Genre> getGenres() { return genres; }
    public void setGenres(List<Genre> genres) { this.genres = genres; }

    public List<Rating> getRatings() { return ratings; }
    public void setRatings(List<Rating> ratings) { this.ratings = ratings; }

    public List<Comment> getComments() { return comments; }
    public void setComments(List<Comment> comments) { this.comments = comments; }

    public List<WatchHistory> getWatchHistories() { return watchHistories; }
    public void setWatchHistories(List<WatchHistory> watchHistories) { this.watchHistories = watchHistories; }

    public Link getLink() { return link; }
    public void setLink(Link link) { this.link = link; }

    public List<Tag> getTags() { return tags; }
    public void setTags(List<Tag> tags) { this.tags = tags; }

    public Double getAverageRating() { return averageRating != null ? averageRating.doubleValue() : 0.0; }
    public void setAverageRating(Double averageRating) {
        this.averageRating = averageRating != null ? BigDecimal.valueOf(averageRating) : BigDecimal.ZERO;
    }
    public BigDecimal getAverageRatingValue() { return averageRating; }
    public void setAverageRatingValue(BigDecimal averageRating) { this.averageRating = averageRating; }

    public Integer getRatingCount() { return ratingCount != null ? ratingCount : 0; }
    public void setRatingCount(Integer ratingCount) { this.ratingCount = ratingCount; }
    public Integer getTotalRatings() { return getRatingCount(); }
    public void setTotalRatings(Integer totalRatings) { this.ratingCount = totalRatings; }
}
