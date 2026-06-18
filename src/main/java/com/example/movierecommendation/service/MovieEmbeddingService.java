package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.entity.Genre;
import com.example.movierecommendation.repository.MovieRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MovieEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(MovieEmbeddingService.class);

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private OpenAIService openAIService;

    private final Map<Integer, float[]> embeddingCache = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application is ready. Starting background movie embedding indexing...");
        initEmbeddingsInBackground();
    }

    @Async
    public void initEmbeddingsInBackground() {
        if (!openAIService.isEnabled()) {
            log.info("OpenAI is disabled. Skipping movie embedding generation.");
            return;
        }

        try {
            List<Object[]> existingEmbeddings = movieRepository.findAllMovieEmbeddingsOnly();
            int cachedCount = 0;

            for (Object[] row : existingEmbeddings) {
                Integer movieId = (Integer) row[0];
                String embStr = (String) row[1];
                if (embStr != null && !embStr.trim().isEmpty()) {
                    try {
                        float[] vector = deserialize(embStr);
                        if (vector.length > 0) {
                            embeddingCache.put(movieId, vector);
                            cachedCount++;
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }

            log.info("Loaded {} movie embeddings from database to in-memory cache.", cachedCount);

            List<Movie> missingEmbeddings = movieRepository.findMoviesMissingEmbedding();
            if (missingEmbeddings.isEmpty()) {
                log.info("All movies are already vectorized!");
                return;
            }

            log.info("Generating embeddings for {} movies in the background...", missingEmbeddings.size());
            int generated = 0;
            int consecutiveFailures = 0;
            for (Movie movie : missingEmbeddings) {
                if (!openAIService.isEnabled()) break;

                try {
                    String textToEmbed = buildTextToEmbed(movie);
                    List<Double> vectorList = openAIService.getEmbedding(textToEmbed);
                    if (vectorList != null && !vectorList.isEmpty()) {
                        consecutiveFailures = 0; // Reset consecutive failures
                        float[] vector = listToArray(vectorList);
                        movie.setEmbedding(serialize(vector));
                        movieRepository.save(movie);

                        embeddingCache.put(movie.getMovieId(), vector);
                        generated++;
                        if (generated % 10 == 0) {
                            log.info("Generated embeddings for {}/{} movies", generated, missingEmbeddings.size());
                        }
                        Thread.sleep(150);
                    } else {
                        consecutiveFailures++;
                        if (consecutiveFailures >= 5) {
                            log.error("Aborting background movie embedding indexing due to 5 consecutive API failures. Please check network connectivity or API key.");
                            break;
                        }
                    }
                } catch (Exception e) {
                    consecutiveFailures++;
                    log.warn("Failed to generate embedding for movie '{}': {}", movie.getTitle(), e.getMessage());
                    if (consecutiveFailures >= 5) {
                        log.error("Aborting background movie embedding indexing due to 5 consecutive API failures. Please check network connectivity or API key.");
                        break;
                    }
                }
            }
            log.info("✅ Finished generating embeddings. Total generated: {}", generated);

        } catch (Exception e) {
            log.error("Error during movie embedding generation: {}", e.getMessage(), e);
        }
    }

    @Async
    public void generateEmbeddingForMovieAsync(Movie movie) {
        if (!openAIService.isEnabled() || movie == null) return;
        try {
            String textToEmbed = buildTextToEmbed(movie);
            List<Double> vectorList = openAIService.getEmbedding(textToEmbed);
            if (vectorList != null && !vectorList.isEmpty()) {
                float[] vector = listToArray(vectorList);
                movie.setEmbedding(serialize(vector));
                movieRepository.save(movie);
                embeddingCache.put(movie.getMovieId(), vector);
                log.info("Successfully generated and cached embedding for movie '{}' (ID: {})", movie.getTitle(), movie.getMovieId());
            }
        } catch (Exception e) {
            log.warn("Failed to generate embedding for movie '{}' asynchronously: {}", movie.getTitle(), e.getMessage());
        }
    }

    public List<Movie> searchSemantic(String query, int limit) {
        if (!openAIService.isEnabled() || query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<Double> queryVectorList = openAIService.getEmbedding(query);
        if (queryVectorList == null || queryVectorList.isEmpty()) {
            return Collections.emptyList();
        }

        float[] queryVector = listToArray(queryVectorList);

        // Compute scores purely in memory
        List<ScoredMovieId> scoredIds = new ArrayList<>();
        for (Map.Entry<Integer, float[]> entry : embeddingCache.entrySet()) {
            Integer id = entry.getKey();
            float[] movieVector = entry.getValue();
            if (movieVector != null && movieVector.length > 0) {
                double score = cosineSimilarity(queryVector, movieVector);
                scoredIds.add(new ScoredMovieId(id, score));
            }
        }

        // Sort and limit
        List<Integer> topIds = scoredIds.stream()
            .sorted((a, b) -> Double.compare(b.score(), a.score()))
            .limit(limit)
            .map(ScoredMovieId::movieId)
            .collect(Collectors.toList());

        if (topIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Fetch top matching movies from database
        List<Movie> matchedMovies = movieRepository.findAllByIdWithGenres(topIds);
        Map<Integer, Movie> movieMap = matchedMovies.stream()
            .collect(Collectors.toMap(Movie::getMovieId, m -> m));

        // Return movies preserving the search score order and filtering deleted
        return topIds.stream()
            .map(movieMap::get)
            .filter(Objects::nonNull)
            .filter(m -> m.getDeletedAt() == null)
            .collect(Collectors.toList());
    }

    private String buildTextToEmbed(Movie movie) {
        String genres = movie.getGenres() != null ? 
            movie.getGenres().stream().map(Genre::getGenreName).collect(Collectors.joining(", ")) : "";
        String desc = movie.getDescription() != null ? movie.getDescription().trim() : "";
        String actors = movie.getActorsText() != null ? movie.getActorsText().trim() : "";
        String directors = movie.getDirectorsText() != null ? movie.getDirectorsText().trim() : "";
        
        return String.format("Title: %s. Year: %s. Genres: %s. Directors: %s. Actors: %s. Description: %s",
            movie.getTitle(),
            movie.getReleaseYear() != null ? String.valueOf(movie.getReleaseYear()) : "N/A",
            genres,
            directors,
            actors,
            desc
        );
    }

    private float[] listToArray(List<Double> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i).floatValue();
        }
        return arr;
    }

    private String serialize(float[] vector) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) sb.append(",");
        }
        return sb.toString();
    }

    private float[] deserialize(String serialized) {
        if (serialized == null || serialized.isEmpty()) return new float[0];
        String[] parts = serialized.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) return 0.0;
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record ScoredMovie(Movie movie, double score) {}
    private record ScoredMovieId(Integer movieId, double score) {}
}
