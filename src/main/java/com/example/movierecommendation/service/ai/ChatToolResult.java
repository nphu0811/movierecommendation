package com.example.movierecommendation.service.ai;

import com.example.movierecommendation.entity.Movie;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatToolResult {
    private final String tool;
    private final boolean success;
    private final String message;
    private final List<Movie> movies;
    private final Map<String, Object> clientAction;

    public ChatToolResult(String tool, boolean success, String message, List<Movie> movies,
                          Map<String, Object> clientAction) {
        this.tool = tool;
        this.success = success;
        this.message = message;
        this.movies = movies == null ? new ArrayList<>() : movies;
        this.clientAction = clientAction;
    }

    public String getTool() { return tool; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public List<Movie> getMovies() { return movies; }
    public Map<String, Object> getClientAction() { return clientAction; }
}
