package com.example.movierecommendation.entity;

import java.io.Serializable;
import java.util.Objects;

public class SimilarMovieId implements Serializable {
    private Integer movie;
    private Integer similarMovie;

    public SimilarMovieId() {}

    public SimilarMovieId(Integer movie, Integer similarMovie) {
        this.movie = movie;
        this.similarMovie = similarMovie;
    }

    public Integer getMovie() { return movie; }
    public void setMovie(Integer movie) { this.movie = movie; }
    public Integer getSimilarMovie() { return similarMovie; }
    public void setSimilarMovie(Integer similarMovie) { this.similarMovie = similarMovie; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimilarMovieId that = (SimilarMovieId) o;
        return Objects.equals(movie, that.movie) && Objects.equals(similarMovie, that.similarMovie);
    }

    @Override
    public int hashCode() {
        return Objects.hash(movie, similarMovie);
    }
}
