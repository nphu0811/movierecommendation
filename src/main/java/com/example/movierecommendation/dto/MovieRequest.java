package com.example.movierecommendation.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public class MovieRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
    private String title;

    @Size(max = 5000, message = "Description too long")
    private String description;

    @Min(value = 1888, message = "Release year must be after 1888")
    @Max(value = 2100, message = "Release year is invalid")
    private Integer releaseYear;

    @Size(max = 500, message = "Poster URL too long")
    private String posterUrl;

    private List<Integer> genreIds;

    public MovieRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getReleaseYear() { return releaseYear; }
    public void setReleaseYear(Integer releaseYear) { this.releaseYear = releaseYear; }

    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }

    public List<Integer> getGenreIds() { return genreIds; }
    public void setGenreIds(List<Integer> genreIds) { this.genreIds = genreIds; }
}
