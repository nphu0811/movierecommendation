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
import java.util.List;
import java.util.Optional;

@Service
public class MovieService {

    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private GenreRepository genreRepository;
    @Autowired
    private RatingRepository ratingRepository;


    public Page<Movie> getAllMovies(int page, int size) {
        return movieRepository.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()));
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
        List<Movie> results = movieRepository.searchByTitleOrGenre(keyword);
        results.forEach(m -> {
            Double avg = ratingRepository.findAverageRatingByMovieId(m.getMovieId());
            m.setAverageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0);
        });
        return results;
    }

    public List<Movie> getTopRatedMovies(int limit) {
        List<Movie> movies = movieRepository.findTopRatedMovies(PageRequest.of(0, limit));
        movies.forEach(m -> {
            Double avg = ratingRepository.findAverageRatingByMovieId(m.getMovieId());
            m.setAverageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0);
        });
        return movies;
    }

    public List<Movie> getPopularMovies(int limit) {
        return movieRepository.findMostWatchedMovies(PageRequest.of(0, limit));
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
