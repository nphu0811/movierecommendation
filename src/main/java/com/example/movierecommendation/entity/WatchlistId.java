package com.example.movierecommendation.entity;

import java.io.Serializable;
import java.util.Objects;

public class WatchlistId implements Serializable {
    private Integer user;
    private Integer movie;

    public WatchlistId() {}

    public WatchlistId(Integer user, Integer movie) {
        this.user = user;
        this.movie = movie;
    }

    // Getters, Setters, Equals, HashCode
    public Integer getUser() { return user; }
    public void setUser(Integer user) { this.user = user; }

    public Integer getMovie() { return movie; }
    public void setMovie(Integer movie) { this.movie = movie; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WatchlistId that = (WatchlistId) o;
        return Objects.equals(user, that.user) && Objects.equals(movie, that.movie);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, movie);
    }
}
