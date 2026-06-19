package com.example.movierecommendation.service;

import com.example.movierecommendation.dto.MovieRequest;
import com.example.movierecommendation.entity.*;
import com.example.movierecommendation.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Set;

@Service
public class MovieService {

    private static final Set<String> RESERVED_GENRE_NAMES = Set.of("testgenre", "new genre");

    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private GenreRepository genreRepository;
    @Autowired
    private RatingRepository ratingRepository;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;
    @Autowired
    private OpenAIService openAIService;
    @Autowired
    private MovieEmbeddingService movieEmbeddingService;

    public Page<Movie> getAllMovies(int page, int size) {
        return movieRepository.findByDeletedAtIsNull(PageRequest.of(page, size, Sort.by("movieId").ascending()));
    }

    public Page<Movie> getFilteredMovies(String keyword, Integer genreId, Integer year, Double minRating, String sortBy, int page, int size) {
        StringBuilder countJpql = new StringBuilder("SELECT COUNT(DISTINCT m) FROM Movie m LEFT JOIN m.genres g WHERE m.deletedAt IS NULL");
        StringBuilder selectJpql = new StringBuilder("SELECT DISTINCT m FROM Movie m LEFT JOIN m.genres g WHERE m.deletedAt IS NULL");
        
        Map<String, Object> params = new HashMap<>();
        StringBuilder filterConditions = new StringBuilder();

        if (keyword != null && !keyword.trim().isEmpty()) {
            filterConditions.append(" AND (LOWER(m.title) LIKE LOWER(:keyword) ")
                            .append("OR LOWER(g.genreName) LIKE LOWER(:keyword) ")
                            .append("OR LOWER(m.description) LIKE LOWER(:keyword) ")
                            .append("OR LOWER(m.actorsText) LIKE LOWER(:keyword) ")
                            .append("OR LOWER(m.directorsText) LIKE LOWER(:keyword))");
            params.put("keyword", "%" + keyword.trim().toLowerCase() + "%");
        }

        if (genreId != null) {
            filterConditions.append(" AND g.genreId = :genreId");
            params.put("genreId", genreId);
        }

        if (year != null) {
            filterConditions.append(" AND m.releaseYear = :year");
            params.put("year", year);
        }

        if (minRating != null) {
            filterConditions.append(" AND m.averageRating >= :minRating");
            params.put("minRating", minRating);
        }

        countJpql.append(filterConditions);
        selectJpql.append(filterConditions);

        // Sorting
        String orderClause = " ORDER BY m.movieId ASC"; // default
        if (sortBy != null) {
            switch (sortBy) {
                case "newest":
                    orderClause = " ORDER BY m.createdAt DESC, m.movieId ASC";
                    break;
                case "top_rated":
                    orderClause = " ORDER BY m.averageRating DESC, m.movieId ASC";
                    break;
                case "most_watched":
                    orderClause = " ORDER BY m.ratingCount DESC, m.movieId ASC";
                    break;
                case "title_az":
                    orderClause = " ORDER BY m.title ASC, m.movieId ASC";
                    break;
                case "release_year":
                    orderClause = " ORDER BY m.releaseYear DESC, m.movieId ASC";
                    break;
            }
        }
        selectJpql.append(orderClause);

        // Execute count query
        jakarta.persistence.TypedQuery<Long> countQuery = entityManager.createQuery(countJpql.toString(), Long.class);
        params.forEach(countQuery::setParameter);
        long totalElements = countQuery.getSingleResult();

        // Execute select query with pagination
        jakarta.persistence.TypedQuery<Movie> selectQuery = entityManager.createQuery(selectJpql.toString(), Movie.class);
        params.forEach(selectQuery::setParameter);
        selectQuery.setFirstResult(page * size);
        selectQuery.setMaxResults(size);
        List<Movie> content = selectQuery.getResultList();

        return new org.springframework.data.domain.PageImpl<>(content, PageRequest.of(page, size), totalElements);
    }

    private void enrichWithRatings(List<Movie> movies) {
        // Rating stats are maintained by DB trigger in movies.average_rating/rating_count.
    }

    public Optional<Movie> findById(Integer id) {
        return movieRepository.findById(id).filter(movie -> movie.getDeletedAt() == null);
    }

    public List<Movie> searchMovies(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<Movie> vectorResults = movieRepository.searchByDatabaseVector(keyword);
        List<Movie> textResults = movieRepository.searchByTitleOrGenre(keyword);
        Map<Integer, Movie> merged = new LinkedHashMap<>();
        for (Movie movie : vectorResults) {
            merged.put(movie.getMovieId(), movie);
        }
        for (Movie movie : textResults) {
            merged.putIfAbsent(movie.getMovieId(), movie);
        }
        List<Movie> results = new ArrayList<>(merged.values());
        return results;
    }

    public List<Movie> searchMoviesByTitleOnly(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return movieRepository.searchByTitleOnly(keyword, PageRequest.of(0, 6));
    }

    public List<Movie> searchMoviesDBVector(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<Movie> dbVector = movieRepository.searchByDatabaseVector(keyword);
        if (openAIService.isEnabled()) {
            List<Movie> semantic = movieEmbeddingService.searchSemantic(keyword, 15);
            Map<Integer, Movie> merged = new LinkedHashMap<>();
            for (Movie m : semantic) {
                merged.put(m.getMovieId(), m);
            }
            for (Movie m : dbVector) {
                merged.putIfAbsent(m.getMovieId(), m);
            }
            return new ArrayList<>(merged.values());
        }
        return dbVector;
    }

    public List<Movie> searchMoviesByVector(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Double> queryVector = buildWeightedVector(keyword, 1.0);
        if (queryVector.isEmpty()) {
            return Collections.emptyList();
        }

        String normalizedKeyword = normalize(keyword);
        List<Movie> results = movieRepository.findAllWithGenres().stream()
            .distinct()
            .map(movie -> new MovieVectorScore(movie, vectorScore(movie, queryVector, normalizedKeyword)))
            .filter(scored -> scored.score >= 0.08)
            .sorted(Comparator.comparingDouble(MovieVectorScore::score).reversed()
                .thenComparing(scored -> scored.movie().getTitle(), String.CASE_INSENSITIVE_ORDER))
            .map(MovieVectorScore::movie)
            .collect(Collectors.toList());
        return results;
    }

    public List<Movie> searchMoviesTextOnly(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<Movie> results = movieRepository.searchByTitleOrGenre(keyword);
        return results;
    }

    private double vectorScore(Movie movie, Map<String, Double> queryVector, String normalizedKeyword) {
        Map<String, Double> movieVector = new HashMap<>();
        addToVector(movieVector, buildWeightedVector(movie.getTitle(), 4.0));
        addToVector(movieVector, buildWeightedVector(movie.getDescription(), 1.0));
        if (movie.getReleaseYear() != null) {
            addToVector(movieVector, buildWeightedVector(String.valueOf(movie.getReleaseYear()), 2.0));
        }
        if (movie.getGenres() != null) {
            for (Genre genre : movie.getGenres()) {
                addToVector(movieVector, buildWeightedVector(genre.getGenreName(), 3.0));
            }
        }

        double score = cosineSimilarity(queryVector, movieVector);
        String normalizedTitle = normalize(movie.getTitle());
        if (!normalizedKeyword.isEmpty() && normalizedTitle.equals(normalizedKeyword)) {
            score += 0.45;
        } else if (!normalizedKeyword.isEmpty() && normalizedTitle.startsWith(normalizedKeyword)) {
            score += 0.25;
        } else if (!normalizedKeyword.isEmpty() && normalizedTitle.contains(normalizedKeyword)) {
            score += 0.15;
        }
        return score;
    }

    private Map<String, Double> buildWeightedVector(String text, double weight) {
        Map<String, Double> vector = new HashMap<>();
        for (String token : tokenize(text)) {
            vector.merge(token, weight, Double::sum);
        }
        return vector;
    }

    private List<String> tokenize(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        return withoutAccents.replace('đ', 'd').replace('Đ', 'D').toLowerCase()
            .replaceAll("[^a-z0-9]+", " ")
            .trim();
    }

    private void addToVector(Map<String, Double> target, Map<String, Double> source) {
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), Double::sum);
        }
    }

    private double cosineSimilarity(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        double dot = 0.0;
        for (Map.Entry<String, Double> entry : a.entrySet()) {
            dot += entry.getValue() * b.getOrDefault(entry.getKey(), 0.0);
        }
        double normA = Math.sqrt(a.values().stream().mapToDouble(v -> v * v).sum());
        double normB = Math.sqrt(b.values().stream().mapToDouble(v -> v * v).sum());
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (normA * normB);
    }

    private record MovieVectorScore(Movie movie, double score) {}

    public List<Movie> getTopRatedMovies(int limit) {
        List<Movie> movies = movieRepository.findTopRatedMovies(PageRequest.of(0, limit));
        return movies;
    }

    public List<Movie> getPopularMovies(int limit) {
        List<Movie> movies = movieRepository.findMostWatchedMovies(PageRequest.of(0, limit));
        return movies;
    }

    public List<Movie> getLatestReleases(int limit) {
        return movieRepository.findLatestReleases(PageRequest.of(0, limit));
    }

    @Transactional
    public Movie createMovie(MovieRequest request) {
        Movie movie = new Movie();
        movie.setTitle(request.getTitle());
        movie.setDescription(request.getDescription());
        movie.setReleaseYear(request.getReleaseYear());
        movie.setPosterUrl(request.getPosterUrl());
        movie.setTrailerKey(request.getTrailerKey());
        movie.setBackdropUrl(request.getBackdropUrl());
        if (request.getGenreIds() != null) {
            movie.setGenres(genreRepository.findAllById(request.getGenreIds()));
        }
        Movie saved = movieRepository.save(movie);
        try {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        movieEmbeddingService.generateEmbeddingForMovieAsync(saved);
                    }
                }
            );
        } catch (IllegalStateException e) {
            movieEmbeddingService.generateEmbeddingForMovieAsync(saved);
        }
        return saved;
    }

    @Transactional
    public Movie updateMovie(Integer id, MovieRequest request) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Movie not found"));
        movie.setTitle(request.getTitle());
        movie.setDescription(request.getDescription());
        movie.setReleaseYear(request.getReleaseYear());
        movie.setPosterUrl(request.getPosterUrl());
        movie.setTrailerKey(request.getTrailerKey());
        movie.setBackdropUrl(request.getBackdropUrl());
        if (request.getGenreIds() != null) {
            movie.setGenres(genreRepository.findAllById(request.getGenreIds()));
        }
        Movie saved = movieRepository.save(movie);
        try {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        movieEmbeddingService.generateEmbeddingForMovieAsync(saved);
                    }
                }
            );
        } catch (IllegalStateException e) {
            movieEmbeddingService.generateEmbeddingForMovieAsync(saved);
        }
        return saved;
    }

    @Transactional
    public void deleteMovie(Integer id) {
        movieRepository.deleteById(id);
    }

    public long countMovies() {
        return movieRepository.findByDeletedAtIsNull(PageRequest.of(0, 1)).getTotalElements();
    }

    public List<Genre> getAllGenres() {
        return genreRepository.findAllPublicGenres();
    }

    @Transactional
    public Genre createGenre(String name) {
        String normalized = normalizeGenreName(name);
        if (genreRepository.existsByGenreNameIgnoreCase(normalized)) {
            throw new RuntimeException("Genre already exists");
        }
        Genre genre = new Genre();
        genre.setGenreName(normalized);
        return genreRepository.save(genre);
    }

    static String normalizeGenreName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Genre name is required");
        }
        String normalized = name.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new IllegalArgumentException("Genre name must contain 1 to 100 characters");
        }
        if (RESERVED_GENRE_NAMES.contains(normalized.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Test genre names are not allowed");
        }
        return normalized;
    }

    @Transactional
    public void deleteGenre(Integer id) {
        genreRepository.deleteById(id);
    }

    public List<Movie> findByGenreIdsExcluding(List<Integer> genreIds, List<Integer> excludeIds, int limit) {
        return movieRepository.findByGenreIdsAndNotInIds(genreIds, excludeIds, PageRequest.of(0, limit));
    }

    @Transactional
    public String getMovieAiSummary(Integer movieId) {
        Movie movie = movieRepository.findById(movieId)
            .orElseThrow(() -> new RuntimeException("Movie not found"));
            
        if (movie.getAiSummary() != null && !movie.getAiSummary().trim().isEmpty()) {
            return movie.getAiSummary();
        }
        
        String summary = null;
        try {
            summary = openAIService.generateMovieSummary(movie.getTitle(), movie.getDescription());
        } catch (Exception e) {
            // Logged inside OpenAIService
        }
        
        if (summary == null || summary.trim().isEmpty()) {
            String genres = movie.getGenres() != null ? movie.getGenres().stream().map(Genre::getGenreName).collect(Collectors.joining(", ")) : "N/A";
            String desc = movie.getDescription();
            String descSnippet = (desc != null && desc.length() > 150) ? desc.substring(0, 147) + "..." : desc;
            summary = String.format("Phim '%s' (%d) thuộc thể loại %s. %s Phim có điểm đánh giá trung bình %.1f/5 với %d lượt đánh giá.", 
                movie.getTitle(), 
                movie.getReleaseYear() != null ? movie.getReleaseYear() : 2026,
                genres, 
                descSnippet != null ? descSnippet : "",
                movie.getAverageRating(),
                movie.getRatingCount());
        }
        
        movie.setAiSummary(summary);
        movieRepository.save(movie);
        return summary;
    }
}
