package com.example.movierecommendation.controller;

import com.example.movierecommendation.entity.*;
import com.example.movierecommendation.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
public class MovieController {

    @Autowired
    private MovieService movieService;
    @Autowired
    private InteractionService interactionService;
    @Autowired
    private RecommendationService recommendationService;
    @Autowired
    private UserService userService;

    @GetMapping("/movies")
    public String listMovies(@RequestParam(name = "page", defaultValue = "0") int page,
                             @RequestParam(name = "size", defaultValue = "12") int size,
                             @AuthenticationPrincipal UserDetails userDetails,
                             Model model) {
        Page<Movie> moviePage = movieService.getAllMovies(page, size);
        model.addAttribute("moviePage", moviePage);
        model.addAttribute("allGenres", movieService.getAllGenres());
        if (userDetails != null) {
            model.addAttribute("currentUser", userService.getCurrentUser(userDetails.getUsername()));
        }
        return "movie/list";
    }

    @GetMapping("/movies/{id}")
    public String movieDetail(@PathVariable("id") Integer id,
                              @AuthenticationPrincipal UserDetails userDetails,
                              Model model) {
        Optional<Movie> opt = movieService.findById(id);
        if (!opt.isPresent()) return "redirect:/movies";

        Movie movie = opt.get();
        model.addAttribute("movie", movie);
        model.addAttribute("comments", interactionService.getCommentsByMovie(id));

        Integer currentUserId = null;
        if (userDetails != null) {
            User currentUser = userService.getCurrentUser(userDetails.getUsername());
            currentUserId = currentUser.getUserId();
            model.addAttribute("currentUser", currentUser);

            Optional<Rating> ratingOpt = interactionService.getUserRating(currentUserId, id);
            int userRating = 0;
            if (ratingOpt.isPresent()) {
                userRating = ratingOpt.get().getRating();
            }
            model.addAttribute("userRating", userRating);
            model.addAttribute("inWatchlist", interactionService.isInWatchlist(currentUserId, id));
            model.addAttribute("hasWatched", interactionService.hasWatched(currentUserId, id));
        }
        model.addAttribute("similarMovies", recommendationService.getSimilarMovies(movie, currentUserId));
        return "movie/detail";
    }

    @PostMapping("/api/movies/{id}/rate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> rateMovie(
            @PathVariable("id") Integer id,
            @RequestParam(name = "score") Integer score,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).build();
        User user = userService.getCurrentUser(userDetails.getUsername());
        interactionService.rateMovie(user.getUserId(), id, score);
        Double avg = interactionService.getAverageRating(id);
        Long count = interactionService.getRatingCount(id);
        Map<String, Object> result = new HashMap<>();
        result.put("average", avg != null ? avg : 0.0);
        result.put("count", count != null ? count : 0L);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/movies/{id}/watch")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markWatched(
            @PathVariable("id") Integer id,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).build();
        User user = userService.getCurrentUser(userDetails.getUsername());
        interactionService.markAsWatched(user.getUserId(), id);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "watched");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/movies/{id}/watchlist")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleWatchlist(
            @PathVariable("id") Integer id,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).build();
        User user = userService.getCurrentUser(userDetails.getUsername());
        boolean added = interactionService.toggleWatchlist(user.getUserId(), id);
        Map<String, Object> result = new HashMap<>();
        result.put("added", added);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/movies/{id}/comment")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addComment(
            @PathVariable("id") Integer id,
            @RequestParam(name = "text") String text,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).build();
        User user = userService.getCurrentUser(userDetails.getUsername());
        Comment comment = interactionService.addComment(user.getUserId(), id, text);
        Map<String, Object> result = new HashMap<>();
        result.put("commentId", comment.getCommentId());
        result.put("username", user.getUsername());
        result.put("text", comment.getCommentText());
        result.put("createdAt", comment.getCreatedAt().toString());
        return ResponseEntity.ok(result);
    }
}
