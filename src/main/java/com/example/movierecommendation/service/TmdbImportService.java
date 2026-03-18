package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.repository.MovieRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class TmdbImportService {

    @Value("${tmdb.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final MovieRepository movieRepository;

    public TmdbImportService(RestTemplate restTemplate, MovieRepository movieRepository) {
        this.restTemplate = restTemplate;
        this.movieRepository = movieRepository;
    }

    public void importPopularMovies() {

        String url = "https://api.themoviedb.org/3/movie/popular?api_key=" + apiKey;

        Map<String,Object> response = restTemplate.getForObject(url, Map.class);

        List<Map<String,Object>> results = (List<Map<String,Object>>) response.get("results");

        for (Map<String,Object> m : results) {

            Movie movie = new Movie();

            movie.setTitle((String) m.get("title"));
            movie.setDescription((String) m.get("overview"));

            movieRepository.save(movie);
        }
    }
}