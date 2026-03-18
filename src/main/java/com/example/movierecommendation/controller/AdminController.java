package com.example.movierecommendation.controller;

import com.example.movierecommendation.dto.MovieRequest;
import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.service.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    
    @Autowired 
    private TmdbImportService tmdbImportService;
    @Autowired private MovieService movieService;
    @Autowired private UserService userService;
    @Autowired private InteractionService interactionService;
    @Autowired private PosterFetchService posterFetchService;
    @Autowired private SeedDataService seedDataService;

    private void addCurrentUser(UserDetails ud, Model model) {
        if (ud != null) {
            User u = userService.getCurrentUser(ud.getUsername());
            model.addAttribute("currentUser", u);
        }
    }

    @GetMapping
    public String dashboard(@AuthenticationPrincipal UserDetails ud, Model model) {
        addCurrentUser(ud, model);
        model.addAttribute("totalUsers",    userService.countUsers());
        model.addAttribute("totalMovies",   movieService.countMovies());
        model.addAttribute("totalRatings",  interactionService.countAllRatings());
        model.addAttribute("totalComments", interactionService.countAllComments());
        model.addAttribute("activeUsers",   interactionService.countActiveUsers());
        model.addAttribute("topRated",      movieService.getTopRatedMovies(5));
        model.addAttribute("popular",       movieService.getPopularMovies(5));
        model.addAttribute("posterRunning", posterFetchService.isRunning());
        model.addAttribute("posterDone",    posterFetchService.getDone());
        model.addAttribute("posterTotal",   posterFetchService.getTotal());
        return "admin/dashboard";
    }

    @PostMapping("/fetch-posters")
    public String fetchPosters(RedirectAttributes redirect) {
        if (posterFetchService.isRunning()) {
            redirect.addFlashAttribute("info", "Poster fetch đang chạy: "
                + posterFetchService.getDone() + "/" + posterFetchService.getTotal());
        } else {
            posterFetchService.fetchAllPosters();
            redirect.addFlashAttribute("success", "✅ Poster fetch started!");
        }
        return "redirect:/admin";
    }

    @GetMapping("/poster-status")
    @ResponseBody
    public Map<String, Object> posterStatus() {
        return Map.of(
            "running", posterFetchService.isRunning(),
            "done",    posterFetchService.getDone(),
            "total",   posterFetchService.getTotal()
        );
    }

    @PostMapping("/seed-data")
    public String seedData(RedirectAttributes redirect) {
        if (seedDataService.isRunning()) {
            redirect.addFlashAttribute("info", "Seed đang chạy: "
                + seedDataService.getDone() + "/" + seedDataService.getTotal());
        } else {
            seedDataService.seedRatingsAndComments();
            redirect.addFlashAttribute("success", "✅ Seed data started!");
        }
        return "redirect:/admin";
    }

    @GetMapping("/seed-status")
    @ResponseBody
    public Map<String, Object> seedStatus() {
        return Map.of(
            "running",  seedDataService.isRunning(),
            "done",     seedDataService.getDone(),
            "total",    seedDataService.getTotal(),
            "ratings",  seedDataService.getRatingsAdded(),
            "comments", seedDataService.getCommentsAdded()
        );
    }

    @GetMapping("/movies")
    public String manageMovies(@RequestParam(name = "page", defaultValue = "0") int page,
                               @AuthenticationPrincipal UserDetails ud, Model model) {
        addCurrentUser(ud, model);
        model.addAttribute("moviePage", movieService.getAllMovies(page, 15));
        model.addAttribute("allGenres", movieService.getAllGenres());
        return "admin/movies";
    }

    @GetMapping("/movies/new")
    public String newMovieForm(@AuthenticationPrincipal UserDetails ud, Model model) {
        addCurrentUser(ud, model);
        model.addAttribute("movieRequest", new MovieRequest());
        model.addAttribute("allGenres",    movieService.getAllGenres());
        return "admin/movie-form";
    }

    @PostMapping("/movies/new")
    public String createMovie(@Valid @ModelAttribute MovieRequest req,
                              BindingResult result,
                              @AuthenticationPrincipal UserDetails ud,
                              Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            addCurrentUser(ud, model);
            model.addAttribute("allGenres", movieService.getAllGenres());
            return "admin/movie-form";
        }
        try {
            movieService.createMovie(req);
            redirect.addFlashAttribute("success", "Movie added successfully");
        } catch (Exception e) {
            log.error("Failed to create movie: {}", e.getMessage());
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/movies";
    }

    @GetMapping("/movies/{id}/edit")
    public String editMovieForm(@PathVariable("id") Integer id,
                                @AuthenticationPrincipal UserDetails ud, Model model) {
        addCurrentUser(ud, model);
        movieService.findById(id).ifPresent(m -> model.addAttribute("movie", m));
        model.addAttribute("allGenres", movieService.getAllGenres());
        return "admin/movie-form";
    }

    @PostMapping("/movies/{id}/edit")
    public String updateMovie(@PathVariable("id") Integer id,
                              @Valid @ModelAttribute MovieRequest req,
                              BindingResult result,
                              @AuthenticationPrincipal UserDetails ud,
                              Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            addCurrentUser(ud, model);
            model.addAttribute("allGenres", movieService.getAllGenres());
            return "admin/movie-form";
        }
        try {
            movieService.updateMovie(id, req);
            redirect.addFlashAttribute("success", "Movie updated");
        } catch (Exception e) {
            log.error("Failed to update movie {}: {}", id, e.getMessage());
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/movies";
    }

    @PostMapping("/movies/{id}/delete")
    public String deleteMovie(@PathVariable("id") Integer id, RedirectAttributes redirect) {
        movieService.deleteMovie(id);
        redirect.addFlashAttribute("success", "Movie deleted");
        return "redirect:/admin/movies";
    }

    @GetMapping("/genres")
    public String manageGenres(@AuthenticationPrincipal UserDetails ud, Model model) {
        addCurrentUser(ud, model);
        model.addAttribute("genres", movieService.getAllGenres());
        return "admin/genres";
    }

    @PostMapping("/genres/new")
    public String createGenre(@RequestParam(name = "name") String name, RedirectAttributes redirect) {
        try {
            movieService.createGenre(name);
            redirect.addFlashAttribute("success", "Genre added");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/genres";
    }

    @PostMapping("/genres/{id}/delete")
    public String deleteGenre(@PathVariable("id") Integer id, RedirectAttributes redirect) {
        movieService.deleteGenre(id);
        redirect.addFlashAttribute("success", "Genre deleted");
        return "redirect:/admin/genres";
    }

    @GetMapping("/users")
    public String manageUsers(@RequestParam(name = "page", defaultValue = "0") int page,
                               @AuthenticationPrincipal UserDetails ud, Model model) {
        addCurrentUser(ud, model);
        // FIX: Dùng pagination thay vì findAll()
        model.addAttribute("userPage", userService.getAllUsersPaged(page, 20));
        return "admin/users";
    }

    @PostMapping("/users/{id}/toggle")
    public String toggleUser(@PathVariable("id") Integer id, RedirectAttributes redirect) {
        userService.toggleUserStatus(id);
        redirect.addFlashAttribute("success", "User status updated");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable("id") Integer id, RedirectAttributes redirect) {
        userService.deleteUser(id);
        redirect.addFlashAttribute("success", "User deleted");
        return "redirect:/admin/users";
    }
    @GetMapping("/import-movies")
    @ResponseBody
    public String importMovies() {

        tmdbImportService.importPopularMovies();

        return "Movies imported successfully";
    }
}
