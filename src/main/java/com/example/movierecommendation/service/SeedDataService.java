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

import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SeedDataService {

    private static final Logger log = LoggerFactory.getLogger(SeedDataService.class);
    private static final String TMDB = "https://api.themoviedb.org/3";

    @Autowired private MovieRepository movieRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RatingRepository ratingRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private WatchHistoryRepository watchHistoryRepository;
    @Autowired private GenreRepository genreRepository;
    @Autowired private ApiSyncLogRepository apiSyncLogRepository;
    @Autowired private VideoTimelineRepository videoTimelineRepository;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

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



    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void seedDemoUsersAndInteractions() {
        log.info("Starting demo data seeding and metadata populating...");
        LocalDateTime startedAt = LocalDateTime.now();
        ApiSyncLog syncLog = new ApiSyncLog();
        syncLog.setProvider("SYSTEM");
        syncLog.setAction("SEED_DEMO_DATA");
        syncLog.setStatus("RUNNING");
        syncLog.setStartedAt(startedAt);
        syncLog = apiSyncLogRepository.save(syncLog);

        try {
            // 1. Seed the 3 demo users
            String encodedPassword = passwordEncoder.encode("123456");
            int successCount = 0;

            User actionUser = userRepository.findByEmail("action.demo@example.com").orElse(null);
            if (actionUser == null) {
                actionUser = new User();
                actionUser.setEmail("action.demo@example.com");
                actionUser.setUsername("ActionDemo");
                actionUser.setPasswordHash(encodedPassword);
                actionUser.setRole("USER");
                actionUser.setIsActive(true);
                actionUser.setIsEmailVerified(true);
                actionUser = userRepository.save(actionUser);
                successCount++;
            }

            User comedyUser = userRepository.findByEmail("comedy.demo@example.com").orElse(null);
            if (comedyUser == null) {
                comedyUser = new User();
                comedyUser.setEmail("comedy.demo@example.com");
                comedyUser.setUsername("ComedyDemo");
                comedyUser.setPasswordHash(encodedPassword);
                comedyUser.setRole("USER");
                comedyUser.setIsActive(true);
                comedyUser.setIsEmailVerified(true);
                comedyUser = userRepository.save(comedyUser);
                successCount++;
            }

            User newUser = userRepository.findByEmail("new.demo@example.com").orElse(null);
            if (newUser == null) {
                newUser = new User();
                newUser.setEmail("new.demo@example.com");
                newUser.setUsername("NewDemo");
                newUser.setPasswordHash(encodedPassword);
                newUser.setRole("USER");
                newUser.setIsActive(true);
                newUser.setIsEmailVerified(true);
                newUser = userRepository.save(newUser);
                successCount++;
            }

            // 2. Fetch movies to seed ratings
            List<Movie> allMovies = movieRepository.findAll();
            
            // 3. Mock metadata for movies if not present
            String[] mockActors = {
                "Leonardo DiCaprio, Brad Pitt, Margot Robbie",
                "Christian Bale, Heath Ledger, Gary Oldman",
                "Matthew McConaughey, Anne Hathaway, Jessica Chastain",
                "Robert Downey Jr., Chris Evans, Scarlett Johansson",
                "Tom Hanks, Robin Wright, Gary Sinise",
                "Keanu Reeves, Laurence Fishburne, Carrie-Anne Moss",
                "Johnny Depp, Orlando Bloom, Keira Knightley",
                "Liam Neeson, Ben Kingsley, Ralph Fiennes",
                "Marlon Brando, Al Pacino, James Caan",
                "John Travolta, Uma Thurman, Samuel L. Jackson"
            };
            String[] mockDirectors = {
                "Christopher Nolan",
                "Quentin Tarantino",
                "Steven Spielberg",
                "Martin Scorsese",
                "James Cameron",
                "Francis Ford Coppola",
                "Ridley Scott",
                "David Fincher",
                "Peter Jackson",
                "Stanley Kubrick"
            };

            Random rand = new Random();
            int metadataUpdated = 0;
            for (Movie m : allMovies) {
                boolean updated = false;
                if (m.getActorsText() == null || m.getActorsText().trim().isEmpty()) {
                    String title = m.getTitle().toLowerCase();
                    if (title.contains("inception")) {
                        m.setActorsText("Leonardo DiCaprio, Joseph Gordon-Levitt, Elliot Page");
                    } else if (title.contains("interstellar")) {
                        m.setActorsText("Matthew McConaughey, Anne Hathaway, Jessica Chastain");
                    } else if (title.contains("dark knight") || title.contains("batman")) {
                        m.setActorsText("Christian Bale, Heath Ledger, Aaron Eckhart");
                    } else if (title.contains("matrix")) {
                        m.setActorsText("Keanu Reeves, Laurence Fishburne, Carrie-Anne Moss");
                    } else if (title.contains("avatar")) {
                        m.setActorsText("Sam Worthington, Zoe Saldana, Sigourney Weaver");
                    } else if (title.contains("godfather")) {
                        m.setActorsText("Marlon Brando, Al Pacino, James Caan");
                    } else {
                        m.setActorsText(mockActors[rand.nextInt(mockActors.length)]);
                    }
                    updated = true;
                }
                if (m.getDirectorsText() == null || m.getDirectorsText().trim().isEmpty()) {
                    String title = m.getTitle().toLowerCase();
                    if (title.contains("inception") || title.contains("interstellar") || title.contains("dark knight") || title.contains("batman")) {
                        m.setDirectorsText("Christopher Nolan");
                    } else if (title.contains("matrix")) {
                        m.setDirectorsText("Lana Wachowski, Lilly Wachowski");
                    } else if (title.contains("avatar") || title.contains("titanic")) {
                        m.setDirectorsText("James Cameron");
                    } else if (title.contains("godfather")) {
                        m.setDirectorsText("Francis Ford Coppola");
                    } else if (title.contains("pulp fiction")) {
                        m.setDirectorsText("Quentin Tarantino");
                    } else {
                        m.setDirectorsText(mockDirectors[rand.nextInt(mockDirectors.length)]);
                    }
                    updated = true;
                }
                if (updated) {
                    movieRepository.save(m);
                    metadataUpdated++;
                }
            }

            if (!allMovies.isEmpty()) {
                // Setup Action user taste: 5 stars for Action/Adventure movies, 1-2 stars for Romance
                List<Movie> actionMovies = allMovies.stream()
                    .filter(m -> m.getGenres() != null && m.getGenres().stream()
                        .anyMatch(g -> g.getGenreName().equalsIgnoreCase("Action") || g.getGenreName().equalsIgnoreCase("Adventure")))
                    .limit(5)
                    .collect(Collectors.toList());
                    
                List<Movie> romanceMovies = allMovies.stream()
                    .filter(m -> m.getGenres() != null && m.getGenres().stream()
                        .anyMatch(g -> g.getGenreName().equalsIgnoreCase("Romance")))
                    .limit(3)
                    .collect(Collectors.toList());

                for (Movie m : actionMovies) {
                    if (ratingRepository.findByUserUserIdAndMovieMovieId(actionUser.getUserId(), m.getMovieId()).isEmpty()) {
                        Rating r = new Rating();
                        r.setUser(actionUser);
                        r.setMovie(m);
                        r.setRating(5.0);
                        ratingRepository.save(r);
                    }
                    if (watchHistoryRepository.findByUserUserIdAndMovieMovieId(actionUser.getUserId(), m.getMovieId()).isEmpty()) {
                        WatchHistory wh = new WatchHistory();
                        wh.setUser(actionUser);
                        wh.setMovie(m);
                        wh.setProgress(90.0);
                        wh.setWatchDuration(5400);
                        wh.setWatchedAt(LocalDateTime.now().minusDays(2));
                        watchHistoryRepository.save(wh);
                    }
                }

                for (Movie m : romanceMovies) {
                    if (ratingRepository.findByUserUserIdAndMovieMovieId(actionUser.getUserId(), m.getMovieId()).isEmpty()) {
                        Rating r = new Rating();
                        r.setUser(actionUser);
                        r.setMovie(m);
                        r.setRating(2.0);
                        ratingRepository.save(r);
                    }
                }

                // Setup Comedy/Romance user taste: 5 stars for Comedy/Romance, watches them
                List<Movie> comedyMovies = allMovies.stream()
                    .filter(m -> m.getGenres() != null && m.getGenres().stream()
                        .anyMatch(g -> g.getGenreName().equalsIgnoreCase("Comedy") || g.getGenreName().equalsIgnoreCase("Romance")))
                    .limit(6)
                    .collect(Collectors.toList());

                for (Movie m : comedyMovies) {
                    if (ratingRepository.findByUserUserIdAndMovieMovieId(comedyUser.getUserId(), m.getMovieId()).isEmpty()) {
                        Rating r = new Rating();
                        r.setUser(comedyUser);
                        r.setMovie(m);
                        r.setRating(5.0);
                        ratingRepository.save(r);
                    }
                    if (watchHistoryRepository.findByUserUserIdAndMovieMovieId(comedyUser.getUserId(), m.getMovieId()).isEmpty()) {
                        WatchHistory wh = new WatchHistory();
                        wh.setUser(comedyUser);
                        wh.setMovie(m);
                        wh.setProgress(85.0);
                        wh.setWatchDuration(4800);
                        wh.setWatchedAt(LocalDateTime.now().minusDays(1));
                        watchHistoryRepository.save(wh);
                    }
                }
            }

            // Seed timeline events
            seedTimelineEvents(allMovies);

            syncLog.setStatus("SUCCESS");
            syncLog.setTotalItems(3 + metadataUpdated);
            syncLog.setSuccessCount(3 + metadataUpdated);
            syncLog.setFinishedAt(LocalDateTime.now());
            apiSyncLogRepository.save(syncLog);
            log.info("✅ Demo data seeding finished successfully!");

        } catch (Exception e) {
            log.error("❌ Seeding demo data failed: {}", e.getMessage(), e);
            syncLog.setStatus("FAILED");
            syncLog.setErrorMessage(e.getMessage());
            syncLog.setFinishedAt(LocalDateTime.now());
            apiSyncLogRepository.save(syncLog);
        }
    }

    private void seedTimelineEvents(List<Movie> allMovies) {
        log.info("Seeding video timeline events...");
        for (Movie m : allMovies) {
            String title = m.getTitle().toLowerCase();
            List<VideoTimeline> existing = videoTimelineRepository.findByMovieMovieIdOrderByTimestampSecondsAsc(m.getMovieId());
            if (!existing.isEmpty()) continue;

            if (title.contains("inception")) {
                videoTimelineRepository.save(new VideoTimeline(m, 15, "Cobb giới thiệu khái niệm trộm cắp giấc mơ (Dream sharing).", "What is the most resilient parasite? An idea."));
                videoTimelineRepository.save(new VideoTimeline(m, 45, "Cobb giải thích cách hoạt động của giấc mơ cho Ariadne.", "You create the world of the dream. We bring the subject into that dream."));
                videoTimelineRepository.save(new VideoTimeline(m, 75, "Phân cảnh hành động hành lang xoay tròn và thành phố Paris gập lại.", "It's never just a dream, is it?"));
                videoTimelineRepository.save(new VideoTimeline(m, 120, "Cảnh hành động đỉnh điểm dồn dập trước khi kết thúc trailer.", "You need to wake up!"));
            } else if (title.contains("interstellar")) {
                videoTimelineRepository.save(new VideoTimeline(m, 20, "Cooper chia tay con gái Murph trước chuyến đi.", "We're researchers, pioneers, not caretakers."));
                videoTimelineRepository.save(new VideoTimeline(m, 55, "Giáo sư Brand giải thích nhiệm vụ cứu loài người bằng lỗ giun.", "We must reach far beyond our own lifespans."));
                videoTimelineRepository.save(new VideoTimeline(m, 90, "Cảnh tàu Endurance phóng vào không gian và đi qua lỗ giun.", "Do not go gentle into that good night."));
                videoTimelineRepository.save(new VideoTimeline(m, 135, "Cooper khóc khi xem lại các tin nhắn ghi hình gửi từ Trái Đất.", "We'll find a way, we always do."));
            } else if (title.contains("dark knight") || title.contains("batman")) {
                videoTimelineRepository.save(new VideoTimeline(m, 10, "Joker cướp ngân hàng và giới thiệu bản thân.", "Whatever doesn't kill you, simply makes you stranger."));
                videoTimelineRepository.save(new VideoTimeline(m, 40, "Joker đột nhập buổi tiệc của Bruce Wayne.", "Let's put a smile on that face!"));
                videoTimelineRepository.save(new VideoTimeline(m, 70, "Batman đua xe Batpod rượt đuổi Joker trên đường phố Gotham.", "You have nothing, nothing to threaten me with!"));
                videoTimelineRepository.save(new VideoTimeline(m, 110, "Cảnh vụ nổ lớn và Joker cười điên dại trong xe cảnh sát.", "Why so serious?"));
            } else if (title.contains("matrix")) {
                videoTimelineRepository.save(new VideoTimeline(m, 15, "Morpheus giải thích Ma trận là gì cho Neo.", "The Matrix is everywhere. It is all around us."));
                videoTimelineRepository.save(new VideoTimeline(m, 50, "Neo chọn giữa viên thuốc màu đỏ và màu xanh.", "You take the blue pill, the story ends. You take the red pill, you stay in Wonderland."));
                videoTimelineRepository.save(new VideoTimeline(m, 85, "Neo né đạn trên sân thượng (Cảnh Bullet-time nổi tiếng).", "I'm trying to free your mind, Neo."));
                videoTimelineRepository.save(new VideoTimeline(m, 120, "Morpheus nói về niềm tin cứu thế của Neo.", "He is the One."));
            } else if (title.contains("avatar")) {
                videoTimelineRepository.save(new VideoTimeline(m, 20, "Jake Sully đặt chân đến hành tinh Pandora huyền ảo.", "You are not in Kansas anymore. You are on Pandora."));
                videoTimelineRepository.save(new VideoTimeline(m, 55, "Jake Sully học cách cưỡi sinh vật bay Ikran.", "How do I know if he chooses me? He will try to kill you."));
                videoTimelineRepository.save(new VideoTimeline(m, 95, "Cuộc chiến khốc liệt bảo vệ Cây Hồn (Tree of Souls).", "This is our land!"));
                videoTimelineRepository.save(new VideoTimeline(m, 130, "Jake Sully mở mắt thức tỉnh hoàn toàn trong cơ thể Avatar.", "I see you."));
            }
        }
    }
}
