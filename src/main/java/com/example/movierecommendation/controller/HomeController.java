package com.example.movierecommendation.controller;

import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
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

    @GetMapping({"/", "/home"})
    public String home(@AuthenticationPrincipal UserDetails userDetails, Model model) {

        User currentUser = null;
        if (userDetails != null) {
            currentUser = userService.getCurrentUser(userDetails.getUsername());
            model.addAttribute("currentUser", currentUser);

            // Chạy recommendations và genrePicks song song thay vì tuần tự
            // (AI call tốn 1-3s nên chạy song song tiết kiệm thời gian đáng kể)
            final User finalUser = currentUser;
            ExecutorService exec = Executors.newFixedThreadPool(2);
            Future<List<Movie>> recFuture = exec.submit(() ->
                recommendationService.getPersonalizedRecommendations(finalUser.getUserId()));
            Future<List<Movie>> genreFuture = exec.submit(() ->
                recommendationService.getGenreBasedRecommendations(finalUser.getUserId()));

            try {
                model.addAttribute("recommendations",
                    recFuture.get(12, TimeUnit.SECONDS));   // timeout 12s
                model.addAttribute("genrePicks",
                    genreFuture.get(3, TimeUnit.SECONDS));  // genre nhanh hơn
            } catch (TimeoutException e) {
                model.addAttribute("recommendations",
                    recommendationService.getTrendingMoviesForUser(currentUser.getUserId()));
                model.addAttribute("genrePicks", Collections.emptyList());
            } catch (Exception e) {
                model.addAttribute("recommendations",
                    recommendationService.getTrendingMoviesForUser(currentUser.getUserId()));
                model.addAttribute("genrePicks", Collections.emptyList());
            } finally {
                exec.shutdown();
            }
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
    public String search(@RequestParam(name = "q") String q,
                         @AuthenticationPrincipal UserDetails userDetails,
                         Model model) {
        model.addAttribute("movies", movieService.searchMovies(q));
        model.addAttribute("keyword", q);
        if (userDetails != null) {
            model.addAttribute("currentUser",
                userService.getCurrentUser(userDetails.getUsername()));
        }
        return "search-results";
    }

    @GetMapping("/api/search/autocomplete")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> autocomplete(
            @RequestParam(name = "q") String q) {
        if (q == null || q.trim().length() < 1) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<Movie> movies = movieService.searchMovies(q.trim());
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