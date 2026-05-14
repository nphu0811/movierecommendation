package com.example.movierecommendation.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "similar_movies")
@IdClass(SimilarMovieId.class)
public class SimilarMovie {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Id
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "similar_movie_id", nullable = false)
    private Movie similarMovie;

    @Column(name = "similarity_score", precision = 5, scale = 2)
    private BigDecimal similarityScore;

    public SimilarMovie() {}

    public Movie getMovie() { return movie; }
    public void setMovie(Movie movie) { this.movie = movie; }
    public Movie getSimilarMovie() { return similarMovie; }
    public void setSimilarMovie(Movie similarMovie) { this.similarMovie = similarMovie; }
    public BigDecimal getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(BigDecimal similarityScore) { this.similarityScore = similarityScore; }
}
