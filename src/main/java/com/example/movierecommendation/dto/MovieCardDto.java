package com.example.movierecommendation.dto;

import java.util.List;

public class MovieCardDto {
    private Integer movieId;
    private String title;
    private String posterUrl;
    private Integer releaseYear;
    private List<String> genres;
    private Double averageRating;
    private String reason;

    public MovieCardDto() {}

    public MovieCardDto(Integer movieId, String title, String posterUrl, Integer releaseYear, List<String> genres, Double averageRating, String reason) {
        this.movieId = movieId;
        this.title = title;
        this.posterUrl = posterUrl;
        this.releaseYear = releaseYear;
        this.genres = genres;
        this.averageRating = averageRating;
        this.reason = reason;
    }

    public Integer getMovieId() { return movieId; }
    public void setMovieId(Integer movieId) { this.movieId = movieId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }

    public Integer getReleaseYear() { return releaseYear; }
    public void setReleaseYear(Integer releaseYear) { this.releaseYear = releaseYear; }

    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }

    public Double getAverageRating() { return averageRating; }
    public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
