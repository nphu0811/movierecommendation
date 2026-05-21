package com.example.movierecommendation.controller;

import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.*;

@Controller
public class HomeController {

    @Autowired private MovieService movieService;
    @Autowired private RecommendationService recommendationService;
    @Autowired private UserService userService;
    @Autowired private InteractionService interactionService;
    @Autowired private SearchHistoryService searchHistoryService;
    @Autowired @Qualifier("homePageExecutor") private Executor homeExecutor;

    @GetMapping({"/", "/home"})
    public String home(@AuthenticationPrincipal UserDetails userDetails, Model model) {

        User currentUser = null;
        if (userDetails != null) {
            currentUser = userService.getCurrentUser(userDetails.getUsername());
            model.addAttribute("currentUser", currentUser);

            // Run recommendations and genre picks in parallel (AI call may take 1-3s).
            // Use shared homePageExecutor instead of creating a new pool per request.
            final User finalUser = currentUser;

            CompletableFuture<List<Movie>> recFuture = CompletableFuture.supplyAsync(
                () -> recommendationService.getPersonalizedRecommendations(finalUser.getUserId()),
                homeExecutor);
            CompletableFuture<List<Movie>> genreFuture = CompletableFuture.supplyAsync(
                () -> recommendationService.getGenreBasedRecommendations(finalUser.getUserId()),
                homeExecutor);

            try {
                model.addAttribute("recommendations", recFuture.get(2, TimeUnit.SECONDS)); // faster failover
            } catch (TimeoutException e) {
                recFuture.cancel(true);
                model.addAttribute("recommendations",
                    recommendationService.getTrendingMoviesForUser(currentUser.getUserId()));
            } catch (Exception e) {
                model.addAttribute("recommendations",
                    recommendationService.getTrendingMoviesForUser(currentUser.getUserId()));
            }

            try {
                model.addAttribute("genrePicks", genreFuture.get(1, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                genreFuture.cancel(true);
                model.addAttribute("genrePicks", Collections.emptyList());
            } catch (Exception e) {
                model.addAttribute("genrePicks", Collections.emptyList());
            }

            // Add Continue Watching (recent history with progress)
            model.addAttribute("continueWatching", interactionService.getRecentWatchHistory(currentUser.getUserId(), 10));
        }

        if (currentUser != null) {
            model.addAttribute("trending", recommendationService.getTrendingMoviesForUser(currentUser.getUserId()));
            model.addAttribute("topRated", recommendationService.getTopRatedMoviesForUser(currentUser.getUserId()));
        } else {
            model.addAttribute("trending", recommendationService.getTrendingMovies());
            model.addAttribute("topRated", recommendationService.getTopRatedMovies());
        }
        model.addAttribute("newReleases", movieService.getAllMovies(0, 8).getContent());
        model.addAttribute("allGenres", movieService.getAllGenres());
        return "home";
    }

    @GetMapping("/search")
    public String search(@RequestParam(name = "q", required = false) String q,
                         @RequestParam(name = "source", required = false, defaultValue = "search_page") String source,
                         @AuthenticationPrincipal UserDetails userDetails,
                         HttpServletRequest request,
                         HttpSession session,
                         Model model) {
        
        long startTime = System.currentTimeMillis();
        User currentUser = null;
        CompletableFuture<List<Movie>> recFuture = null;

        if (userDetails != null) {
            currentUser = userService.getCurrentUser(userDetails.getUsername());
            model.addAttribute("currentUser", currentUser);
            
            // Fire recommendations in background — don't block search
            final User finalUser = currentUser;
            recFuture = CompletableFuture.supplyAsync(
                () -> recommendationService.getPersonalizedRecommendations(finalUser.getUserId()),
                homeExecutor);
        }

        if (q == null || q.trim().isEmpty()) {
            // No search query — resolve recommendations now
            resolveRecommendations(model, recFuture, currentUser, null);
            model.addAttribute("allGenres", movieService.getAllGenres());
            model.addAttribute("trendingSearches", searchHistoryService.getTrendingSearches("24h"));
            return "search/index";
        }

        // --- Run search queries in parallel with recommendations ---
        String keyword = q.trim();
        CompletableFuture<List<Movie>> vectorFuture = CompletableFuture.supplyAsync(
            () -> movieService.searchMoviesDBVector(keyword), homeExecutor);
        CompletableFuture<List<Movie>> textFuture = CompletableFuture.supplyAsync(
            () -> movieService.searchMoviesTextOnly(keyword), homeExecutor);

        List<Movie> vectorMoviesRaw;
        List<Movie> textMovies;
        try {
            vectorMoviesRaw = vectorFuture.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            vectorMoviesRaw = Collections.emptyList();
        }
        try {
            textMovies = textFuture.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            textMovies = Collections.emptyList();
        }

        // Deduplicate by ID and Title+Year (to handle duplicate DB records)
        Set<Integer> seenIds = new HashSet<>();
        Set<String> seenTitles = new HashSet<>();
        List<Movie> movies = new ArrayList<>();

        for (Movie movie : vectorMoviesRaw) {
            String key = (movie.getTitle() + "|" + movie.getReleaseYear()).toLowerCase();
            if (seenIds.add(movie.getMovieId()) && seenTitles.add(key)) {
                movies.add(movie);
            }
        }
        for (Movie movie : textMovies) {
            String key = (movie.getTitle() + "|" + movie.getReleaseYear()).toLowerCase();
            if (seenIds.add(movie.getMovieId()) && seenTitles.add(key)) {
                movies.add(movie);
            }
        }

        // Build "similar movies" from genres of top search results
        Set<Integer> genreIds = new LinkedHashSet<>();
        int topN = Math.min(5, movies.size());
        for (int i = 0; i < topN; i++) {
            Movie m = movies.get(i);
            if (m.getGenres() != null) {
                for (var g : m.getGenres()) genreIds.add(g.getGenreId());
            }
        }
        List<Movie> similarMovies = Collections.emptyList();
        if (!genreIds.isEmpty()) {
            List<Integer> excludeIds = new ArrayList<>(seenIds);
            if (excludeIds.isEmpty()) excludeIds.add(-1);
            similarMovies = movieService.findByGenreIdsExcluding(new ArrayList<>(genreIds), excludeIds, 20);
        }

        Set<Integer> allSeenIds = new HashSet<>(seenIds);
        for (Movie sm : similarMovies) {
            allSeenIds.add(sm.getMovieId());
        }

        // Resolve recommendations (should be done by now since search took time)
        resolveRecommendations(model, recFuture, currentUser, allSeenIds);

        model.addAttribute("movies", movies);
        model.addAttribute("similarMovies", similarMovies);
        model.addAttribute("keyword", keyword);
        model.addAttribute("trendingSearches", searchHistoryService.getTrendingSearches("24h"));

        // Log the search query in search_history
        long latencyMs = System.currentTimeMillis() - startTime;
        int resultCount = movies.size();
        Long loggedSearchId = null;
        try {
            var searchRecord = searchHistoryService.logSearch(
                currentUser,
                keyword,
                resultCount,
                session.getId(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                source,
                latencyMs
            );
            if (searchRecord != null) {
                loggedSearchId = searchRecord.getSearchId();
            }
        } catch (Exception e) {
            // Service handles it internally, this is just a safeguard
        }
        model.addAttribute("searchId", loggedSearchId);

        return "search/index";
    }

    private void resolveRecommendations(Model model, CompletableFuture<List<Movie>> recFuture, User currentUser, Set<Integer> excludeIds) {
        List<Movie> recs;
        if (recFuture != null && currentUser != null) {
            try {
                recs = recFuture.get(1500, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                recFuture.cancel(true);
                recs = recommendationService.getTrendingMoviesForUser(currentUser.getUserId());
            }
        } else if (currentUser != null) {
            recs = recommendationService.getTrendingMoviesForUser(currentUser.getUserId());
        } else {
            recs = recommendationService.getTrendingMovies();
        }

        if (excludeIds != null && !excludeIds.isEmpty() && recs != null) {
            List<Movie> filteredRecs = new ArrayList<>();
            for (Movie m : recs) {
                if (!excludeIds.contains(m.getMovieId())) {
                    filteredRecs.add(m);
                }
            }
            recs = filteredRecs;
        }
        model.addAttribute("recommendations", recs);
    }

    @GetMapping("/api/search/autocomplete")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> autocomplete(
            @RequestParam(name = "q") String q) {
        if (q == null || q.trim().length() < 1) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        String keyword = q.trim();
        List<Movie> movies = movieService.searchMoviesByTitleOnly(keyword);
        
        List<Map<String, Object>> results = new ArrayList<>();
        int limit = Math.min(movies.size(), 6);
        for (int i = 0; i < limit; i++) {
            Movie m = movies.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getMovieId());
            item.put("title", m.getTitle());
            item.put("year", m.getReleaseYear());
            item.put("poster", m.getPosterUrl());
            List<String> genreNames = new ArrayList<>();
            if (m.getGenres() != null) {
                for (var g : m.getGenres()) genreNames.add(g.getGenreName());
            }
            item.put("genres", genreNames);
            results.add(item);
        }
        return ResponseEntity.ok(results);
    }
}
