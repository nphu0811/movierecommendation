package com.example.movierecommendation.dto;

import java.util.List;

public class ChatResponse {
    private String type; // TEXT, MOVIE_CARDS
    private String message;
    private String reply; // For backward compatibility
    private List<MovieCardDto> movies;

    public ChatResponse() {}

    public ChatResponse(String type, String message, List<MovieCardDto> movies) {
        this.type = type;
        this.message = message;
        this.reply = message;
        this.movies = movies;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getMessage() { return message; }
    public void setMessage(String message) {
        this.message = message;
        this.reply = message;
    }

    public String getReply() { return reply; }
    public void setReply(String reply) {
        this.reply = reply;
        this.message = reply;
    }

    public List<MovieCardDto> getMovies() { return movies; }
    public void setMovies(List<MovieCardDto> movies) { this.movies = movies; }
}
