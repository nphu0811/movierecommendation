package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.repository.MovieRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class PosterFetchService {

    private static final Logger log = LoggerFactory.getLogger(PosterFetchService.class);
    private static final String TMDB   = "https://api.themoviedb.org/3";
    private static final String IMG342 = "https://image.tmdb.org/t/p/w342";
    private static final String IMG780 = "https://image.tmdb.org/t/p/w780";

    @Autowired private MovieRepository movieRepository;
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
    public void fetchAllPosters() {
        if (running) return;
        if (apiKey == null || apiKey.isBlank()) {
            log.error("tmdb.api.key not configured");
            return;
        }
        running = true; done = 0;
        try {
            // Lấy phim cần fetch: poster sai format HOẶC chưa có description
            List<Movie> toFix = movieRepository.findAll().stream()
                .filter(m -> (m.getPosterUrl() != null && m.getPosterUrl().matches(".*\\/\\d+\\.jpg"))
                          || m.getDescription() == null || m.getDescription().isEmpty()
                          || m.getTrailerKey() == null)
                .toList();

            total = toFix.size();
            log.info("Fetching TMDB data for {} movies", total);
            List<Movie> batch = new ArrayList<>();

            for (Movie movie : toFix) {
                try {
                    // Extract tmdbId từ poster_url hiện tại
                    String tmdbId = null;
                    if (movie.getPosterUrl() != null && movie.getPosterUrl().matches(".*\\/\\d+\\.jpg")) {
                        String u = movie.getPosterUrl();
                        tmdbId = u.substring(u.lastIndexOf('/') + 1, u.lastIndexOf('.'));
                    }
                    if (tmdbId == null) { done++; continue; }

                    // Fetch movie details
                    Map detail = rest.getForObject(url("/movie/" + tmdbId), Map.class);
                    if (detail != null) {
                        // Poster
                        if (detail.get("poster_path") != null)
                            movie.setPosterUrl(IMG342 + detail.get("poster_path"));

                        // Description (overview)
                        if (detail.get("overview") != null && !detail.get("overview").toString().isEmpty())
                            movie.setDescription(detail.get("overview").toString());

                        // Backdrop
                        if (detail.get("backdrop_path") != null)
                            movie.setBackdropUrl(IMG780 + detail.get("backdrop_path"));
                    }

                    // Fetch trailer
                    try {
                        Map videos = rest.getForObject(url("/movie/" + tmdbId + "/videos"), Map.class);
                        if (videos != null && videos.get("results") instanceof List results) {
                            for (Object r : results) {
                                if (r instanceof Map v) {
                                    if ("YouTube".equals(v.get("site"))
                                        && "Trailer".equals(v.get("type"))
                                        && v.get("key") != null) {
                                        movie.setTrailerKey(v.get("key").toString());
                                        break;
                                    }
                                }
                            }
                            // Fallback: lấy Teaser nếu không có Trailer
                            if (movie.getTrailerKey() == null) {
                                for (Object r : results) {
                                    if (r instanceof Map v) {
                                        if ("YouTube".equals(v.get("site")) && v.get("key") != null) {
                                            movie.setTrailerKey(v.get("key").toString());
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // video fetch thất bại không sao
                    }

                    batch.add(movie);
                    done++;

                    if (batch.size() >= 50) {
                        movieRepository.saveAll(batch);
                        batch.clear();
                        log.info("Progress: {}/{}", done, total);
                    }
                    Thread.sleep(260); // ~3.8 req/sec

                } catch (Exception e) {
                    // 404 = phim không có trên TMDB, bỏ qua không log
                    done++;
                }
            }
            if (!batch.isEmpty()) movieRepository.saveAll(batch);
            long withPoster = movieRepository.findAll().stream()
                .filter(m -> m.getPosterUrl() != null && m.getPosterUrl().contains("/p/w342/"))
                .count();
            log.info("✅ TMDB fetch complete: {}/{} processed, {} movies have posters", done, total, withPoster);
        } finally {
            running = false;
        }
    }
}
