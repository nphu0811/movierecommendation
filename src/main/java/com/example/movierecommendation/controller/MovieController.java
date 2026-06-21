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
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
    private UserService userService;
    @Autowired
    private MovieFacade movieFacade;
    @Autowired
    private AIChatService aiChatService;

    @GetMapping("/movies")
    public String listMovies(@RequestParam(name = "q", required = false) String q,
                             @RequestParam(name = "genreId", required = false) Integer genreId,
                             @RequestParam(name = "year", required = false) Integer year,
                             @RequestParam(name = "minRating", required = false) Double minRating,
                             @RequestParam(name = "sortBy", defaultValue = "newest") String sortBy,
                             @RequestParam(name = "page", defaultValue = "0") int page,
                             @RequestParam(name = "size", defaultValue = "12") int size,
                             @AuthenticationPrincipal UserDetails userDetails,
                             Model model) {
        Page<Movie> moviePage = movieService.getFilteredMovies(q, genreId, year, minRating, sortBy, page, size);
        model.addAttribute("moviePage", moviePage);
        model.addAttribute("allGenres", movieService.getAllGenres());
        
        // Pass filter values back to view
        model.addAttribute("q", q);
        model.addAttribute("genreId", genreId);
        model.addAttribute("year", year);
        model.addAttribute("minRating", minRating);
        model.addAttribute("sortBy", sortBy);

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
            model.addAttribute("userRating", dto.getUserRating());
            model.addAttribute("inWatchlist", dto.isInWatchlist());
            model.addAttribute("hasWatched", dto.isHasWatched());
        }

        // Add server URLs
        String imdbId = (dto.getMovieLink() != null) ? dto.getMovieLink().getImdbId() : null;
        Integer tmdbId = (dto.getMovieLink() != null) ? dto.getMovieLink().getTmdbId() : null;

        String primaryId = (imdbId != null && !imdbId.isBlank()) ? imdbId.trim() : (tmdbId != null ? String.valueOf(tmdbId) : null);

        String streamImdbUrl = null;
        if (imdbId != null && !imdbId.isBlank()) {
            String cleanImdbId = imdbId.trim();
            if (cleanImdbId.startsWith("tt")) {
                cleanImdbId = cleanImdbId.substring(2);
            }
            cleanImdbId = cleanImdbId.replaceFirst("^0+", "");
            if (!cleanImdbId.isEmpty()) {
                streamImdbUrl = "https://streamimdb.ru/embed/movie/tt" + cleanImdbId;
            }
        }

        if (primaryId != null) {
            // Server 1 (Primary) - Advanced local proxy player (se_player.php)
            String localPlayerUrl = "/se_player.php?video_id=" + primaryId;
            if (imdbId == null || imdbId.isBlank()) {
                localPlayerUrl += "&tmdb=1";
            }
            model.addAttribute("server1", localPlayerUrl);

            // Server 2 (Secondary) - SuperEmbed (Multiembed) - Stable, does not redirect localhost
            model.addAttribute("server2", buildSuperEmbedUrl(primaryId));

            if (streamImdbUrl != null) {
                // Server 3 (Tertiary) - streamimdb.ru
                model.addAttribute("server3", streamImdbUrl);
            } else {
                // Server 3 (Tertiary) - 2Embed (works with TMDB ID)
                model.addAttribute("server3", "https://www.2embed.online/embed/movie/" + primaryId);
            }
        }

        // Watch History / Progress
        if (dto.getCurrentUser() != null) {
            // Auto-record in history (initial)
            interactionService.markAsWatched(dto.getCurrentUser().getUserId(), id, null, null);
            
            interactionService.getWatchHistoryEntry(dto.getCurrentUser().getUserId(), id)
                .ifPresent(wh -> model.addAttribute("lastDuration", wh.getWatchDuration()));
        }

        model.addAttribute("similarMovies", dto.getSimilarMovies());

        return "movie/play";
    }

    @GetMapping("/se_player.php")
    public void sePlayer(@RequestParam("video_id") String videoId,
                         @RequestParam(value = "tmdb", defaultValue = "0") int tmdb,
                         @RequestParam(value = "s", required = false) Integer s,
                         @RequestParam(value = "e", required = false) Integer e,
                         @RequestParam(value = "season", required = false) Integer season,
                         @RequestParam(value = "episode", required = false) Integer episode,
                         HttpServletResponse response) throws IOException {
        
        int finalSeason = (season != null) ? season : ((s != null) ? s : 0);
        int finalEpisode = (episode != null) ? episode : ((e != null) ? e : 0);
        
        if (videoId == null || videoId.isBlank()) {
            response.sendError(400, "Missing video_id");
            return;
        }

        String requestUrl = UriComponentsBuilder
                .fromHttpUrl("https://getsuperembed.link/")
                .queryParam("video_id", videoId.trim())
                .queryParam("tmdb", tmdb)
                .queryParam("season", finalSeason)
                .queryParam("episode", finalEpisode)
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

        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            String playerUrl = restTemplate.getForObject(requestUrl, String.class);
            
            if (playerUrl != null && playerUrl.contains("https://")) {
                response.sendRedirect(playerUrl);
            } else {
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write("<span style='color:red'>" + (playerUrl != null ? playerUrl : "Request server didn't respond") + "</span>");
            }
        } catch (Exception ex) {
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("<span style='color:red'>Request server error: " + ex.getMessage() + "</span>");
        }
    }

    @GetMapping("/movies/{id}/play/superembed")
    public String redirectToSuperEmbed(@PathVariable("id") @Min(1) @Max(Integer.MAX_VALUE) Integer id,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : null;
        MovieDetailDTO dto = movieFacade.getMovieDetail(id, username);
        if (dto == null || dto.getMovieLink() == null) {
            return "redirect:/movies/" + id + "/play";
        }

        String imdbId = dto.getMovieLink().getImdbId();
        Integer tmdbId = dto.getMovieLink().getTmdbId();
        String primaryId = (imdbId != null && !imdbId.isBlank()) ? imdbId.trim() : (tmdbId != null ? String.valueOf(tmdbId) : null);

        if (primaryId == null) {
            return "redirect:/movies/" + id + "/play";
        }

        return "redirect:" + buildSuperEmbedUrl(primaryId);
    }

    private String buildSuperEmbedUrl(String imdbId) {
        return UriComponentsBuilder
                .fromHttpUrl("https://multiembed.mov/")
                .queryParam("video_id", imdbId)
                .build()
                .toUriString();
    }

    @PostMapping("/api/movies/{id}/rate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> rateMovie(
            @PathVariable("id") @Min(1) @Max(Integer.MAX_VALUE) Integer id,
            @RequestParam(name = "score") @DecimalMin("0.5") @DecimalMax("5.0") Double score,
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

    @GetMapping("/api/movies/{id}/ai-summary")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAiSummary(
            @PathVariable("id") @Min(1) @Max(Integer.MAX_VALUE) Integer id) {
        String summary = movieService.getMovieAiSummary(id);
        Map<String, Object> result = new HashMap<>();
        result.put("summary", summary);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/movies/{id}/video-chat")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> videoChat(
            @PathVariable("id") @Min(1) @Max(Integer.MAX_VALUE) Integer id,
            @RequestBody Map<String, String> requestBody,
            @AuthenticationPrincipal UserDetails userDetails) {
        String message = requestBody.get("message");
        if (message == null || message.trim().isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Message cannot be empty");
            return ResponseEntity.badRequest().body(err);
        }

        Movie movie = movieService.findById(id)
            .orElseThrow(() -> new RuntimeException("Movie not found"));

        User currentUser = null;
        if (userDetails != null) {
            currentUser = userService.getCurrentUser(userDetails.getUsername());
        }

        String reply = aiChatService.chatAboutVideo(currentUser, movie, message.trim());
        Map<String, Object> result = new HashMap<>();
        result.put("reply", reply);
        return ResponseEntity.ok(result);
    }
}
