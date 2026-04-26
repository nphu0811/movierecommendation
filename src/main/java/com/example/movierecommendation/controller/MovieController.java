package com.example.movierecommendation.controller;

import com.example.movierecommendation.dto.MovieDetailDTO;
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
import org.springframework.web.util.UriComponentsBuilder;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@Validated
public class MovieController {
    private static final String PLAYER_FONT = "Poppins";
    private static final String PLAYER_BG_COLOR = "000000";
    private static final String PLAYER_FONT_COLOR = "ffffff";
    private static final String PLAYER_PRIMARY_COLOR = "34cfeb";
    private static final String PLAYER_SECONDARY_COLOR = "6900e0";
    private static final int PLAYER_LOADER = 1;
    private static final int PREFERRED_SERVER = 0;
    private static final int PLAYER_SOURCES_TOGGLE_TYPE = 2;

    @Autowired
    private MovieService movieService;
    @Autowired
    private InteractionService interactionService;
    @Autowired
    private RecommendationService recommendationService;
    @Autowired
    private UserService userService;
    @Autowired
    private MovieFacade movieFacade;

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
    public String movieDetail(@PathVariable("id") @Min(1) @Max(Integer.MAX_VALUE) Integer id,
                              @AuthenticationPrincipal UserDetails userDetails,
                              Model model) {
        String username = userDetails != null ? userDetails.getUsername() : null;
        MovieDetailDTO dto = movieFacade.getMovieDetail(id, username);
        if (dto == null) return "redirect:/movies";

        model.addAttribute("movie", dto.getMovie());
        model.addAttribute("comments", dto.getComments());
        model.addAttribute("movieLink", dto.getMovieLink());
        model.addAttribute("topTags", dto.getTopTags() != null ? dto.getTopTags() : java.util.Collections.emptyList());
        
        if (dto.getCurrentUser() != null) {
            model.addAttribute("currentUser", dto.getCurrentUser());
            model.addAttribute("userRating", dto.getUserRating());
            model.addAttribute("inWatchlist", dto.isInWatchlist());
            model.addAttribute("hasWatched", dto.isHasWatched());
        }
        
        model.addAttribute("similarMovies", dto.getSimilarMovies());
        return "movie/detail";
    }

    @GetMapping("/movies/{id}/play")
    public String moviePlay(@PathVariable("id") @Min(1) @Max(Integer.MAX_VALUE) Integer id,
                            @AuthenticationPrincipal UserDetails userDetails,
                            Model model) {
        String username = userDetails != null ? userDetails.getUsername() : null;
        MovieDetailDTO dto = movieFacade.getMovieDetail(id, username);
        if (dto == null) return "redirect:/movies";

        model.addAttribute("movie", dto.getMovie());
        model.addAttribute("movieLink", dto.getMovieLink());
        model.addAttribute("comments", dto.getComments());
        if (dto.getCurrentUser() != null) {
            model.addAttribute("currentUser", dto.getCurrentUser());
        }

        // Add server URLs
        String imdbId = (dto.getMovieLink() != null) ? dto.getMovieLink().getImdbId() : null;
        if (imdbId != null && !imdbId.isBlank()) {
            model.addAttribute("server2Embed", "https://www.2embed.online/embed/movie/" + imdbId.trim());
            model.addAttribute("serverSuperEmbed", buildSuperEmbedUrl(imdbId.trim()));
        }

        return "movie/play";
    }

    @GetMapping("/movies/{id}/play/superembed")
    public String redirectToSuperEmbed(@PathVariable("id") @Min(1) @Max(Integer.MAX_VALUE) Integer id,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : null;
        MovieDetailDTO dto = movieFacade.getMovieDetail(id, username);
        if (dto == null || dto.getMovieLink() == null || dto.getMovieLink().getImdbId() == null
                || dto.getMovieLink().getImdbId().isBlank()) {
            return "redirect:/movies/" + id + "/play";
        }

        return "redirect:" + buildSuperEmbedUrl(dto.getMovieLink().getImdbId().trim());
    }

    private String buildSuperEmbedUrl(String imdbId) {
        return UriComponentsBuilder
                .fromHttpUrl("https://getsuperembed.link/")
                .queryParam("video_id", imdbId)
                .queryParam("tmdb", 0)
                .queryParam("season", 0)
                .queryParam("episode", 0)
                .queryParam("player_font", PLAYER_FONT)
                .queryParam("player_bg_color", PLAYER_BG_COLOR)
                .queryParam("player_font_color", PLAYER_FONT_COLOR)
                .queryParam("player_primary_color", PLAYER_PRIMARY_COLOR)
                .queryParam("player_secondary_color", PLAYER_SECONDARY_COLOR)
                .queryParam("player_loader", PLAYER_LOADER)
                .queryParam("preferred_server", PREFERRED_SERVER)
                .queryParam("player_sources_toggle_type", PLAYER_SOURCES_TOGGLE_TYPE)
                .build()
                .toUriString();
    }

    @PostMapping("/api/movies/{id}/rate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> rateMovie(
            @PathVariable("id") @Min(1) @Max(Integer.MAX_VALUE) Integer id,
            @RequestParam(name = "score") @Min(1) @Max(5) Integer score,
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
            @PathVariable("id") @Min(1) @Max(Integer.MAX_VALUE) Integer id,
            @RequestParam(name = "duration", required = false) Integer duration,
            @RequestParam(name = "progress", required = false) Double progress,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).build();
        User user = userService.getCurrentUser(userDetails.getUsername());
        interactionService.markAsWatched(user.getUserId(), id, duration, progress);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "watched");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/movies/{id}/watchlist")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleWatchlist(
            @PathVariable("id") @Min(1) @Max(Integer.MAX_VALUE) Integer id,
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
            @PathVariable("id") @Min(1) @Max(Integer.MAX_VALUE) Integer id,
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

    @PostMapping("/api/movies/{id}/tags")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addTag(
            @PathVariable("id") @Min(1) @Max(Integer.MAX_VALUE) Integer id,
            @RequestParam(name = "tag") String tag,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).build();
        User user = userService.getCurrentUser(userDetails.getUsername());
        try {
            com.example.movierecommendation.entity.Tag saved =
                interactionService.addTag(user.getUserId(), id, tag);
            Map<String, Object> result = new HashMap<>();
            result.put("tagId", saved.getTagId());
            result.put("tag", saved.getTag());
            result.put("username", user.getUsername());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    @DeleteMapping("/api/tags/{tagId}")
    @ResponseBody
    public ResponseEntity<Void> deleteTag(
            @PathVariable("tagId") Integer tagId,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).build();
        User user = userService.getCurrentUser(userDetails.getUsername());
        interactionService.deleteTag(tagId, user.getUserId());
        return ResponseEntity.ok().build();
    }
}
