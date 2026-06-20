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
    @Autowired private MetadataRepairService metadataRepairService;

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
        model.addAttribute("seedRunning",   seedDataService.isRunning());
        model.addAttribute("seedDone",      seedDataService.getDone());
        model.addAttribute("seedTotal",     seedDataService.getTotal());
        model.addAttribute("seedRatingsAdded",  seedDataService.getRatingsAdded());
        model.addAttribute("seedCommentsAdded", seedDataService.getCommentsAdded());
        model.addAttribute("demoSeedEnabled", seedDataService.isDemoSeedEnabled());
        model.addAttribute("repairRunning", metadataRepairService.isRunning());
        model.addAttribute("repairDone",    metadataRepairService.getDone());
        model.addAttribute("repairTotal",   metadataRepairService.getTotal());
        return "admin/dashboard";
    }

    @PostMapping("/fetch-posters")
    public String fetchPosters(RedirectAttributes redirect) {
        if (posterFetchService.isRunning()) {
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            redirect.addFlashAttribute("info", (isVi ? "Poster fetch đang chạy: " : "Poster fetch is running: ")
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
        if (!seedDataService.isDemoSeedEnabled()) {
            redirect.addFlashAttribute("error", "Demo data seeding is disabled in this environment");
        } else if (seedDataService.isRunning()) {
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            redirect.addFlashAttribute("info", (isVi ? "Seed đang chạy: " : "Seed is running: ")
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

    @PostMapping("/repair-metadata")
    public String repairMetadata(RedirectAttributes redirect) {
        if (metadataRepairService.isRunning()) {
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            redirect.addFlashAttribute("info", (isVi ? "Sửa metadata đang chạy: " : "Metadata repair is running: ")
                + metadataRepairService.getDone() + "/" + metadataRepairService.getTotal());
        } else {
            metadataRepairService.repairMetadata();
            redirect.addFlashAttribute("success", "✅ Metadata repair started!");
        }
        return "redirect:/admin";
    }

    @GetMapping("/repair-status")
    @ResponseBody
    public Map<String, Object> repairStatus() {
        return Map.of(
            "running", metadataRepairService.isRunning(),
            "done",    metadataRepairService.getDone(),
            "total",   metadataRepairService.getTotal()
        );
    }

    @PostMapping("/cleanup-movies")
    public String cleanUpMovies(RedirectAttributes redirect) {
        try {
            int cleanedCount = movieService.cleanUpInvalidMovies();
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            if (isVi) {
                redirect.addFlashAttribute("success", "✅ Đã dọn dẹp xong! Đã xóa " + cleanedCount + " bộ phim thiếu poster hoặc IMDb ID.");
            } else {
                redirect.addFlashAttribute("success", "✅ Clean up completed! Removed " + cleanedCount + " movies missing posters or IMDb IDs.");
            }
        } catch (Exception e) {
            log.error("Failed to clean up movies: {}", e.getMessage());
            redirect.addFlashAttribute("error", "Failed to clean up movies: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @GetMapping("/movies")
    public String manageMovies(@RequestParam(name = "q", required = false) String keyword,
                               @RequestParam(name = "page", defaultValue = "0") int page,
                               @AuthenticationPrincipal UserDetails ud, Model model) {
        addCurrentUser(ud, model);
        model.addAttribute("moviePage", movieService.getAllMovies(keyword, page, 15));
        model.addAttribute("allGenres", movieService.getAllGenres());
        model.addAttribute("searchKeyword", keyword);
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
    public String manageUsers(@RequestParam(name = "q", required = false) String keyword,
                              @RequestParam(name = "page", defaultValue = "0") int page,
                              @AuthenticationPrincipal UserDetails ud, Model model) {
        addCurrentUser(ud, model);
        model.addAttribute("userPage", userService.getAllUsersPaged(keyword, page, 20));
        model.addAttribute("searchKeyword", keyword);
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
