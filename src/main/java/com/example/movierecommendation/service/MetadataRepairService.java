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
}
