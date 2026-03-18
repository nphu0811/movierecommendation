package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.*;
import com.example.movierecommendation.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class SeedDataService {

    private static final Logger log = LoggerFactory.getLogger(SeedDataService.class);
    private static final String TMDB = "https://api.themoviedb.org/3";

    @Autowired private MovieRepository movieRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RatingRepository ratingRepository;
    @Autowired private CommentRepository commentRepository;

    @Value("${tmdb.api.key:}") private String apiKey;

    private final RestTemplate rest = new RestTemplate();
    private volatile boolean running = false;
    private volatile int done = 0, total = 0, ratingsAdded = 0, commentsAdded = 0;

    public boolean isRunning() { return running; }
    public int getDone()       { return done; }
    public int getTotal()      { return total; }
    public int getRatingsAdded()  { return ratingsAdded; }
    public int getCommentsAdded() { return commentsAdded; }

    private String url(String path) {
        return TMDB + path + "?api_key=" + apiKey;
    }

    @Async
    public void seedRatingsAndComments() {
        if (running) return;
        if (apiKey == null || apiKey.isBlank()) { log.error("No TMDB API key"); return; }

        running = true; done = 0; ratingsAdded = 0; commentsAdded = 0;

        try {
            // Lấy admin user để gán rating/comment
            User adminUser = userRepository.findByEmail("admin@movierec.com").orElse(null);
            if (adminUser == null) { log.error("Admin user not found"); return; }

            // Lấy các phim có poster (đã fetch TMDB) và chưa có rating
            List<Movie> movies = movieRepository.findAll().stream()
                .filter(m -> m.getPosterUrl() != null
                          && m.getPosterUrl().contains("/p/w342/")
                          && !m.getPosterUrl().matches(".*\\/\\d+\\.jpg"))
                .filter(m -> ratingRepository.findByUserUserIdAndMovieMovieId(
                    adminUser.getUserId(), m.getMovieId()).isEmpty())
                .limit(500) // seed 500 phim phổ biến nhất
                .toList();

            total = movies.size();
            log.info("Seeding ratings+comments for {} movies", total);

            // Tạo thêm 5 fake users để có nhiều ratings đa dạng
            List<User> seedUsers = getOrCreateSeedUsers();

            List<Rating>  ratingBatch  = new ArrayList<>();
            List<Comment> commentBatch = new ArrayList<>();

            for (Movie movie : movies) {
                try {
                    // Extract tmdbId từ poster URL: .../w342/xyzABC.jpg
                    String posterUrl = movie.getPosterUrl();
                    String posterFile = posterUrl.substring(posterUrl.lastIndexOf('/') + 1);
                    // Không có tmdbId trực tiếp nữa - search bằng tên
                    String searchUrl = url("/search/movie") + "&query="
                        + java.net.URLEncoder.encode(movie.getTitle(), "UTF-8")
                        + (movie.getReleaseYear() != null ? "&year=" + movie.getReleaseYear() : "");

                    Map searchResult = rest.getForObject(searchUrl, Map.class);
                    if (searchResult == null) { done++; continue; }

                    List results = (List) searchResult.get("results");
                    if (results == null || results.isEmpty()) { done++; continue; }

                    Map tmdbMovie = (Map) results.get(0);
                    Object tmdbIdObj = tmdbMovie.get("id");
                    if (tmdbIdObj == null) { done++; continue; }

                    String tmdbId = tmdbIdObj.toString();

                    // Lấy vote_average từ TMDB (thang 10) -> convert sang thang 5
                    double voteAvg = 0;
                    Object va = tmdbMovie.get("vote_average");
                    if (va instanceof Number) voteAvg = ((Number) va).doubleValue();

                    // Seed ratings từ fake users dựa trên TMDB score
                    if (voteAvg > 0) {
                        int baseRating = (int) Math.round(voteAvg / 2.0); // 10->5 scale
                        baseRating = Math.max(1, Math.min(5, baseRating));

                        for (User u : seedUsers) {
                            // Chỉ thêm nếu chưa có
                            if (ratingRepository.findByUserUserIdAndMovieMovieId(
                                    u.getUserId(), movie.getMovieId()).isEmpty()) {
                                // Thêm variance ±1
                                int r = baseRating + (new Random().nextInt(3) - 1);
                                r = Math.max(1, Math.min(5, r));
                                Rating rating = new Rating();
                                rating.setUser(u);
                                rating.setMovie(movie);
                                rating.setRating(r);
                                rating.setRatedAt(LocalDateTime.now()
                                    .minusDays(new Random().nextInt(365)));
                                ratingBatch.add(rating);
                                ratingsAdded++;
                            }
                        }
                    }

                    // Fetch reviews từ TMDB
                    try {
                        Map reviewsResp = rest.getForObject(url("/movie/" + tmdbId + "/reviews"), Map.class);
                        if (reviewsResp != null && reviewsResp.get("results") instanceof List reviews) {
                            int count = 0;
                            for (Object r : reviews) {
                                if (count >= 3) break; // Tối đa 3 comment mỗi phim
                                if (r instanceof Map review) {
                                    String content = (String) review.get("content");
                                    String author  = (String) review.get("author");
                                    if (content != null && !content.isBlank()) {
                                        // Truncate nếu quá dài
                                        if (content.length() > 500) content = content.substring(0, 497) + "...";

                                        // Tìm hoặc tạo user với tên reviewer
                                        User commentUser = getOrCreateReviewUser(author, seedUsers);

                                        Comment comment = new Comment();
                                        comment.setUser(commentUser);
                                        comment.setMovie(movie);
                                        comment.setCommentText(content);
                                        comment.setCreatedAt(LocalDateTime.now()
                                            .minusDays(new Random().nextInt(365)));
                                        commentBatch.add(comment);
                                        commentsAdded++;
                                        count++;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // reviews thất bại không sao
                    }

                    done++;

                    // Save batch mỗi 20 phim
                    if (ratingBatch.size() >= 100) {
                        ratingRepository.saveAll(ratingBatch);
                        ratingBatch.clear();
                    }
                    if (commentBatch.size() >= 50) {
                        commentRepository.saveAll(commentBatch);
                        commentBatch.clear();
                    }

                    if (done % 20 == 0) {
                        log.info("Seed progress: {}/{} | ratings: {} | comments: {}",
                            done, total, ratingsAdded, commentsAdded);
                    }

                    Thread.sleep(300); // 2 API calls/phim -> ~6.6 req/sec

                } catch (Exception e) {
                    done++;
                }
            }

            // Save remaining
            if (!ratingBatch.isEmpty())  ratingRepository.saveAll(ratingBatch);
            if (!commentBatch.isEmpty()) commentRepository.saveAll(commentBatch);

            log.info("✅ Seed complete! ratings: {}, comments: {}", ratingsAdded, commentsAdded);

        } finally {
            running = false;
        }
    }

    private List<User> getOrCreateSeedUsers() {
        String[][] seedData = {
            {"moviefan1@seed.com",   "MovieFan",    "USER"},
            {"cinephile2@seed.com",  "Cinephile",   "USER"},
            {"filmcritic3@seed.com", "FilmCritic",  "USER"},
            {"movielover4@seed.com", "MovieLover",  "USER"},
            {"watchdog5@seed.com",   "WatchDog",    "USER"},
        };
        List<User> users = new ArrayList<>();
        for (String[] d : seedData) {
            User u = userRepository.findByEmail(d[0]).orElseGet(() -> {
                User nu = new User();
                nu.setEmail(d[0]);
                nu.setUsername(d[1]);
                nu.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
                nu.setRole(d[2]);
                nu.setIsActive(true);
                return userRepository.save(nu);
            });
            users.add(u);
        }
        return users;
    }

    private User getOrCreateReviewUser(String author, List<User> fallback) {
        if (author == null || author.isBlank()) return fallback.get(0);
        String email = author.replaceAll("[^a-zA-Z0-9]", "").toLowerCase() + "@tmdb.com";
        if (email.length() > 50) email = email.substring(0, 46) + "@tmdb.com";
        final String finalEmail = email;
        return userRepository.findByEmail(finalEmail).orElseGet(() -> {
            try {
                User u = new User();
                u.setEmail(finalEmail);
                String uname = author.length() > 30 ? author.substring(0, 30) : author;
                u.setUsername(uname.replaceAll("[^a-zA-Z0-9_]", ""));
                if (u.getUsername().isBlank()) u.setUsername("reviewer_" + System.currentTimeMillis() % 10000);
                u.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
                u.setRole("USER");
                u.setIsActive(false); // inactive - chỉ để hiện comment
                return userRepository.save(u);
            } catch (Exception e) {
                return fallback.get(new Random().nextInt(fallback.size()));
            }
        });
    }
}
