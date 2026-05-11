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

@Service
public class MovieService {

    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private GenreRepository genreRepository;
    @Autowired
    private RatingRepository ratingRepository;


    public Page<Movie> getAllMovies(int page, int size) {
        Page<Movie> moviePage = movieRepository.findAll(PageRequest.of(page, size, Sort.by("movieId").ascending()));
        enrichWithRatings(moviePage.getContent());
        return moviePage;
    }

    private void enrichWithRatings(List<Movie> movies) {
        if (movies == null || movies.isEmpty()) return;
        List<Integer> ids = movies.stream().map(Movie::getMovieId).collect(Collectors.toList());
        List<Object[]> stats = ratingRepository.findRatingStatsByMovieIds(ids);
        
        Map<Integer, Object[]> statsMap = stats.stream()
            .collect(Collectors.toMap(
                row -> (Integer) row[0],
                row -> row
            ));
            
        for (Movie m : movies) {
            Object[] row = statsMap.get(m.getMovieId());
            if (row != null) {
                Double avg = (Double) row[1];
                Long count = (Long) row[2];
                m.setAverageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0);
                m.setTotalRatings(count != null ? count.intValue() : 0);
            } else {
                m.setAverageRating(0.0);
                m.setTotalRatings(0);
            }
        }
    }

    public Optional<Movie> findById(Integer id) {
        Optional<Movie> opt = movieRepository.findById(id);
        opt.ifPresent(movie -> {
            Double avg = ratingRepository.findAverageRatingByMovieId(id);
            Long count = ratingRepository.countByMovieId(id);
            movie.setAverageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0);
            movie.setTotalRatings(count != null ? count.intValue() : 0);
        });
        return opt;
    }

    public List<Movie> searchMovies(String keyword) {
        List<Movie> vectorResults = searchMoviesByVector(keyword);
        List<Movie> textResults = movieRepository.searchByTitleOrGenre(keyword);
        Map<Integer, Movie> merged = new LinkedHashMap<>();
        for (Movie movie : vectorResults) {
            merged.put(movie.getMovieId(), movie);
        }
        for (Movie movie : textResults) {
            merged.putIfAbsent(movie.getMovieId(), movie);
        }
        List<Movie> results = new ArrayList<>(merged.values());
        enrichWithRatings(results);
        return results;
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
            .map(movie -> new MovieVectorScore(movie, vectorScore(movie, queryVector, normalizedKeyword)))
            .filter(scored -> scored.score >= 0.08)
            .sorted(Comparator.comparingDouble(MovieVectorScore::score).reversed()
                .thenComparing(scored -> scored.movie().getTitle(), String.CASE_INSENSITIVE_ORDER))
            .map(MovieVectorScore::movie)
            .collect(Collectors.toList());
        enrichWithRatings(results);
        return results;
    }

    public List<Movie> searchMoviesTextOnly(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<Movie> results = movieRepository.searchByTitleOrGenre(keyword);
        enrichWithRatings(results);
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
        enrichWithRatings(movies);
        return movies;
    }

    public List<Movie> getPopularMovies(int limit) {
        List<Movie> movies = movieRepository.findMostWatchedMovies(PageRequest.of(0, limit));
        enrichWithRatings(movies);
        return movies;
    }

    @Transactional
    public Movie createMovie(MovieRequest request) {
        Movie movie = new Movie();
        movie.setTitle(request.getTitle());
        movie.setDescription(request.getDescription());
        movie.setReleaseYear(request.getReleaseYear());
        movie.setPosterUrl(request.getPosterUrl());
        if (request.getGenreIds() != null) {
            movie.setGenres(genreRepository.findAllById(request.getGenreIds()));
        }
        return movieRepository.save(movie);
    }

    @Transactional
    public Movie updateMovie(Integer id, MovieRequest request) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Movie not found"));
        movie.setTitle(request.getTitle());
        movie.setDescription(request.getDescription());
        movie.setReleaseYear(request.getReleaseYear());
        movie.setPosterUrl(request.getPosterUrl());
        if (request.getGenreIds() != null) {
            movie.setGenres(genreRepository.findAllById(request.getGenreIds()));
        }
        return movieRepository.save(movie);
    }

    @Transactional
    public void deleteMovie(Integer id) {
        movieRepository.deleteById(id);
    }

    public long countMovies() {
        return movieRepository.count();
    }

    public List<Genre> getAllGenres() {
        return genreRepository.findAll();
    }

    @Transactional
    public Genre createGenre(String name) {
        if (genreRepository.existsByGenreName(name)) {
            throw new RuntimeException("Genre already exists");
        }
        Genre genre = new Genre();
        genre.setGenreName(name);
        return genreRepository.save(genre);
    }

    @Transactional
    public void deleteGenre(Integer id) {
        genreRepository.deleteById(id);
    }
}
