package com.example.movierecommendation.dto;

import com.example.movierecommendation.entity.Comment;
import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.entity.User;

import java.util.List;

public class MovieDetailDTO {
    private Movie movie;
    private List<Comment> comments;
    private User currentUser;
    private int userRating;
    private boolean inWatchlist;
    private boolean hasWatched;
    private List<Movie> similarMovies;

    public Movie getMovie() { return movie; }
    public void setMovie(Movie movie) { this.movie = movie; }

    public List<Comment> getComments() { return comments; }
    public void setComments(List<Comment> comments) { this.comments = comments; }

    public User getCurrentUser() { return currentUser; }
    public void setCurrentUser(User currentUser) { this.currentUser = currentUser; }

    public int getUserRating() { return userRating; }
    public void setUserRating(int userRating) { this.userRating = userRating; }

    public boolean isInWatchlist() { return inWatchlist; }
    public void setInWatchlist(boolean inWatchlist) { this.inWatchlist = inWatchlist; }

    public boolean isHasWatched() { return hasWatched; }
    public void setHasWatched(boolean hasWatched) { this.hasWatched = hasWatched; }

    public List<Movie> getSimilarMovies() { return similarMovies; }
    public void setSimilarMovies(List<Movie> similarMovies) { this.similarMovies = similarMovies; }
}
