package com.example.movierecommendation.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "links")
public class Link {

    @Id
    @Column(name = "movie_id")
    private Integer movieId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @Column(name = "imdb_id", length = 20)
    private String imdbId;

    @Column(name = "tmdb_id")
    private Integer tmdbId;

    public Link() {}

    public Integer getMovieId() { return movieId; }
    public void setMovieId(Integer movieId) { this.movieId = movieId; }

    public Movie getMovie() { return movie; }
    public void setMovie(Movie movie) { this.movie = movie; }

    public String getImdbId() { return imdbId; }
    public void setImdbId(String imdbId) { this.imdbId = imdbId; }

    public Integer getTmdbId() { return tmdbId; }
    public void setTmdbId(Integer tmdbId) { this.tmdbId = tmdbId; }
}
