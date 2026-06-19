package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.Genre;
import com.example.movierecommendation.entity.Link;
import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.repository.GenreRepository;
import com.example.movierecommendation.repository.LinkRepository;
import com.example.movierecommendation.repository.MovieRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TmdbImportService {

    private static final String TMDB_IMAGE = "https://image.tmdb.org/t/p/";
    private static final Map<Integer, String> TMDB_GENRES = Map.ofEntries(
        Map.entry(28, "Action"), Map.entry(12, "Adventure"), Map.entry(16, "Animation"),
        Map.entry(35, "Comedy"), Map.entry(80, "Crime"), Map.entry(99, "Documentary"),
        Map.entry(18, "Drama"), Map.entry(10751, "Children"), Map.entry(14, "Fantasy"),
        Map.entry(36, "History"), Map.entry(27, "Horror"), Map.entry(10402, "Musical"),
        Map.entry(9648, "Mystery"), Map.entry(10749, "Romance"),
        Map.entry(878, "Science Fiction"), Map.entry(53, "Thriller"),
        Map.entry(10752, "War"), Map.entry(37, "Western")
    );

    @Value("${tmdb.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final MovieRepository movieRepository;
    private final LinkRepository linkRepository;
    private final GenreRepository genreRepository;

    public TmdbImportService(RestTemplate restTemplate,
                             MovieRepository movieRepository,
                             LinkRepository linkRepository,
                             GenreRepository genreRepository) {
        this.restTemplate = restTemplate;
        this.movieRepository = movieRepository;
        this.linkRepository = linkRepository;
        this.genreRepository = genreRepository;
    }

    /**
     * Imports TMDB popular movies by stable tmdb_id. Existing movies are
     * enriched in place; retries never create a second row for the same TMDB ID.
     */
    @SuppressWarnings("unchecked")
    public void importPopularMovies() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("TMDB API key is not configured");
        }

        String url = "https://api.themoviedb.org/3/movie/popular?api_key=" + apiKey;
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null || !(response.get("results") instanceof List<?> results)) {
            throw new IllegalStateException("TMDB returned no movie results");
        }

        for (Object raw : results) {
            if (raw instanceof Map<?, ?> item) upsertMovie(item);
        }
    }

    private void upsertMovie(Map<?, ?> item) {
        Integer tmdbId = integerValue(item.get("id"));
        String title = stringValue(item.get("title"));
        Integer releaseYear = TmdbMetadataValidator.releaseYear(stringValue(item.get("release_date")));
        if (tmdbId == null || title == null || title.isBlank()) return;

        Optional<Link> existingLink = linkRepository.findByTmdbId(tmdbId);
        Movie movie = existingLink.map(Link::getMovie)
            .orElseGet(() -> movieRepository
                .findFirstByTitleIgnoreCaseAndReleaseYearAndDeletedAtIsNull(title.trim(), releaseYear)
                .orElseGet(Movie::new));

        movie.setTitle(title.trim());
        movie.setReleaseYear(releaseYear);
        movie.setDescription(stringValue(item.get("overview")));
        movie.setPosterUrl(imageUrl("w342", item.get("poster_path")));
        movie.setBackdropUrl(imageUrl("w780", item.get("backdrop_path")));
        movie.setGenres(resolveGenres(item.get("genre_ids")));
        movie.setMetadataSource("TMDB");
        movie.setMetadataVerifiedAt(LocalDateTime.now());
        Movie saved = movieRepository.save(movie);

        if (existingLink.isEmpty()) {
            Link link = new Link();
            link.setMovie(saved);
            link.setTmdbId(tmdbId);
            linkRepository.save(link);
        }
    }

    private List<Genre> resolveGenres(Object rawGenreIds) {
        List<Genre> genres = new ArrayList<>();
        if (!(rawGenreIds instanceof List<?> ids)) return genres;
        for (Object rawId : ids) {
            String genreName = TMDB_GENRES.get(integerValue(rawId));
            if (genreName == null) continue;
            Genre genre = genreRepository.findByGenreNameIgnoreCase(genreName).orElseGet(() -> {
                Genre created = new Genre();
                created.setGenreName(genreName);
                return genreRepository.save(created);
            });
            genres.add(genre);
        }
        return genres;
    }

    private String imageUrl(String size, Object path) {
        String value = stringValue(path);
        return value == null || value.isBlank() ? null : TMDB_IMAGE + size + value;
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? null : Integer.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
