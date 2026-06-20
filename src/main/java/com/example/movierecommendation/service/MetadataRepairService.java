package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.Link;
import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.repository.LinkRepository;
import com.example.movierecommendation.repository.MovieRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class MetadataRepairService {

    private static final Logger log = LoggerFactory.getLogger(MetadataRepairService.class);
    private static final String TMDB   = "https://api.themoviedb.org/3";
    private static final String IMG342 = "https://image.tmdb.org/t/p/w342";
    private static final String IMG780 = "https://image.tmdb.org/t/p/w780";

    @Autowired private MovieRepository movieRepository;
    @Autowired private LinkRepository linkRepository;
    @Autowired private MetadataRepairHelper repairHelper;
    @Value("${tmdb.api.key:}") private String apiKey;

    private final RestTemplate rest = new RestTemplate();
    private volatile boolean running = false;
    private volatile int done = 0, total = 0;

    public boolean isRunning() { return running; }
    public int getDone()  { return done; }
    public int getTotal() { return total; }

    private String url(String path) {
        return TMDB + path + "?api_key=" + apiKey;
    }

    @Async
    public void repairMetadata() {
        if (running) return;
        if (apiKey == null || apiKey.isBlank()) {
            log.error("tmdb.api.key not configured");
            return;
        }
        running = true;
        done = 0;
        try {
            // Find all movies that have external links
            List<Movie> toCheck = movieRepository.findAllWithExternalLinks();

            total = toCheck.size();
            log.info("Starting metadata repair verification for {} movies", total);

            for (Movie movie : toCheck) {
                try {
                    Link link = movie.getLink();
                    Integer tmdbId = link != null ? link.getTmdbId() : null;
                    boolean needsSearchAndHeal = false;

                    if (tmdbId != null) {
                        // Fetch current TMDB details for this tmdbId
                        Map<?, ?> detail = null;
                        boolean is404 = false;
                        try {
                            detail = rest.getForObject(url("/movie/" + tmdbId), Map.class);
                        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
                            is404 = true;
                        } catch (Exception e) {
                            log.error("Failed to fetch TMDB details for ID {}: {}", tmdbId, e.getMessage());
                        }

                        if (is404 || (detail != null && !TmdbMetadataValidator.matches(movie, detail))) {
                            if (is404) {
                                log.warn("TMDB ID={} not found (404). Triggering repair for movie: '{}' (ID={})", tmdbId, movie.getTitle(), movie.getMovieId());
                            } else {
                                log.warn("Mismatch detected! MovieId={}, Title='{}' ({}), TMDB ID={}, TMDB Title='{}' ({})",
                                    movie.getMovieId(), movie.getTitle(), movie.getReleaseYear(),
                                    tmdbId, detail.get("title"), detail.get("release_date"));
                            }

                            // Clean mismatched metadata and delete incorrect link in a short transaction
                            repairHelper.deleteLink(movie.getMovieId());

                            needsSearchAndHeal = true;
                        } else if (detail != null) {
                            // Ensure the poster, backdrop, and description are correct and up-to-date
                            boolean updated = false;

                            String tmdbPoster = detail.get("poster_path") != null ? (IMG342 + detail.get("poster_path")) : null;
                            if (tmdbPoster != null && !Objects.equals(movie.getPosterUrl(), tmdbPoster)) {
                                movie.setPosterUrl(tmdbPoster);
                                updated = true;
                            }

                            String tmdbBackdrop = detail.get("backdrop_path") != null ? (IMG780 + detail.get("backdrop_path")) : null;
                            if (tmdbBackdrop != null && !Objects.equals(movie.getBackdropUrl(), tmdbBackdrop)) {
                                movie.setBackdropUrl(tmdbBackdrop);
                                updated = true;
                            }

                            String tmdbDesc = detail.get("overview") != null ? detail.get("overview").toString() : null;
                            if (tmdbDesc != null && !Objects.equals(movie.getDescription(), tmdbDesc)) {
                                movie.setDescription(tmdbDesc);
                                updated = true;
                            }

                            if (updated) {
                                movie.setMetadataSource("TMDB");
                                movie.setMetadataVerifiedAt(LocalDateTime.now());
                                movieRepository.save(movie);
                                log.info("Corrected mismatched fields for movie '{}' (ID={}) using TMDB ID={}", movie.getTitle(), movie.getMovieId(), tmdbId);
                            }
                        }
                    } else {
                        // No TMDB ID exists, needs to search and heal
                        needsSearchAndHeal = true;
                    }

                    if (needsSearchAndHeal) {
                        // 3. Search TMDB for the correct tmdbId by Title and Release Year
                        String searchUrl = url("/search/movie") + "&query="
                            + URLEncoder.encode(movie.getTitle(), StandardCharsets.UTF_8)
                            + (movie.getReleaseYear() != null ? "&year=" + movie.getReleaseYear() : "");

                        Map<?, ?> searchResult = rest.getForObject(searchUrl, Map.class);
                        if (searchResult != null && searchResult.get("results") instanceof List<?> results) {
                            Map<?, ?> correctCandidate = null;
                            for (Object r : results) {
                                if (r instanceof Map<?, ?> candidate) {
                                    if (TmdbMetadataValidator.matches(movie, candidate)) {
                                        correctCandidate = candidate;
                                        break;
                                    }
                                }
                            }

                            if (correctCandidate != null) {
                                Integer correctTmdbId = (Integer) correctCandidate.get("id");
                                log.info("Found correct TMDB ID={} for movie '{}'", correctTmdbId, movie.getTitle());

                                String posterUrl = null;
                                String backdropUrl = null;
                                String description = null;
                                String trailerKey = null;

                                // Fetch full details of the correct movie to get backdrop, trailer, etc.
                                Map<?, ?> correctDetail = rest.getForObject(url("/movie/" + correctTmdbId), Map.class);
                                if (correctDetail != null) {
                                    if (correctDetail.get("poster_path") != null)
                                        posterUrl = IMG342 + correctDetail.get("poster_path");

                                    if (correctDetail.get("overview") != null)
                                        description = correctDetail.get("overview").toString();

                                    if (correctDetail.get("backdrop_path") != null)
                                        backdropUrl = IMG780 + correctDetail.get("backdrop_path");
                                }

                                // Fetch trailer for correct movie
                                try {
                                    Map<?, ?> videos = rest.getForObject(url("/movie/" + correctTmdbId + "/videos"), Map.class);
                                    if (videos != null && videos.get("results") instanceof List<?> videoList) {
                                        for (Object rv : videoList) {
                                            if (rv instanceof Map<?, ?> v) {
                                                if ("YouTube".equals(v.get("site"))
                                                    && "Trailer".equals(v.get("type"))
                                                    && v.get("key") != null) {
                                                    trailerKey = v.get("key").toString();
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception videoEx) {
                                    // video fetch failure is fine
                                }

                                // Save correct link and metadata via the helper
                                repairHelper.updateMovieMetadata(movie.getMovieId(), correctTmdbId, posterUrl, backdropUrl, description, trailerKey);
                            } else {
                                log.warn("Could not find correct TMDB match for movie '{}'", movie.getTitle());
                            }
                        }
                    }
                    done++;
                    Thread.sleep(260); // rate limiting
                } catch (Exception e) {
                    log.error("Error processing repair for movieId={}: {}", movie.getMovieId(), e.getMessage());
                    done++;
                }
            }

            log.info("✅ Metadata repair finished: checked {}/{}", done, total);
        } finally {
            running = false;
        }
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void autoRepairDuplicates() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("TMDB API key not configured. Skipping automatic duplicate poster repair.");
            return;
        }
        // Run in a background thread to avoid blocking startup
        new Thread(() -> {
            try {
                // Wait 5 seconds for application startup initialization to settle
                Thread.sleep(5000);
                repairDuplicatePosters();
            } catch (Exception e) {
                log.error("Error during automatic duplicate poster check: {}", e.getMessage(), e);
            }
        }).start();
    }

    public void repairDuplicatePosters() throws Exception {
        log.info("Starting automatic duplicate poster check...");
        List<Movie> all = movieRepository.findAll();
        
        // Group by poster_url
        Map<String, List<Movie>> groups = all.stream()
            .filter(m -> m.getPosterUrl() != null && m.getPosterUrl().startsWith("http"))
            .collect(Collectors.groupingBy(Movie::getPosterUrl));
        
        List<Movie> duplicates = groups.values().stream()
            .filter(g -> g.size() > 1)
            .flatMap(List::stream)
            .toList();
        
        if (duplicates.isEmpty()) {
            log.info("No duplicate posters found.");
            return;
        }
        
        log.warn("Found {} movies with duplicate posters. Cleaning and repairing...", duplicates.size());
        
        for (Movie m : duplicates) {
            log.info("Repairing duplicate poster for movie: '{}' (ID={})", m.getTitle(), m.getMovieId());
            // Delete link and clear metadata
            repairHelper.deleteLink(m.getMovieId());
            
            // Search TMDB for the correct TMDB ID by title and year
            String searchUrl = url("/search/movie") + "&query="
                + java.net.URLEncoder.encode(m.getTitle(), java.nio.charset.StandardCharsets.UTF_8)
                + (m.getReleaseYear() != null ? "&year=" + m.getReleaseYear() : "");
            
            Map<?, ?> searchResult = rest.getForObject(searchUrl, Map.class);
            if (searchResult != null && searchResult.get("results") instanceof List<?> results) {
                Map<?, ?> correctCandidate = null;
                for (Object r : results) {
                    if (r instanceof Map<?, ?> candidate) {
                        if (TmdbMetadataValidator.matches(m, candidate)) {
                            correctCandidate = candidate;
                            break;
                        }
                    }
                }
                
                if (correctCandidate != null) {
                    Integer correctTmdbId = (Integer) correctCandidate.get("id");
                    
                    String posterUrl = null;
                    String backdropUrl = null;
                    String description = null;
                    String trailerKey = null;
                    
                    Map<?, ?> correctDetail = rest.getForObject(url("/movie/" + correctTmdbId), Map.class);
                    if (correctDetail != null) {
                        if (correctDetail.get("poster_path") != null)
                            posterUrl = IMG342 + correctDetail.get("poster_path");
                        if (correctDetail.get("overview") != null)
                            description = correctDetail.get("overview").toString();
                        if (correctDetail.get("backdrop_path") != null)
                            backdropUrl = IMG780 + correctDetail.get("backdrop_path");
                    }
                    
                    // Save correct link and metadata via the helper
                    repairHelper.updateMovieMetadata(m.getMovieId(), correctTmdbId, posterUrl, backdropUrl, description, trailerKey);
                    log.info("Successfully repaired duplicate poster for movie '{}' with TMDB ID={}", m.getTitle(), correctTmdbId);
                } else {
                    log.warn("Could not find correct TMDB match for movie '{}'", m.getTitle());
                }
            }
            Thread.sleep(300); // rate limit
        }
        log.info("✅ Automatic duplicate poster check and repair finished.");
    }
}
