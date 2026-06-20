package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.Link;
import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.repository.LinkRepository;
import com.example.movierecommendation.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataRepairServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private MetadataRepairHelper repairHelper;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private MetadataRepairService repairService;

    @Test
    void whenMovieMatchesTmbdDetails_noRepairIsDone() {
        // Setup a matching movie
        Movie movie = new Movie();
        movie.setMovieId(1);
        movie.setTitle("The Matrix");
        movie.setReleaseYear(1999);
        movie.setPosterUrl("https://image.tmdb.org/t/p/w342/matrix.jpg");

        Link link = new Link();
        link.setMovie(movie);
        link.setTmdbId(603);
        movie.setLink(link);

        when(movieRepository.findAllWithExternalLinks()).thenReturn(List.of(movie));

        // Mock TMDB response details
        Map<String, Object> details = Map.of(
            "id", 603,
            "title", "The Matrix",
            "release_date", "1999-03-30",
            "overview", "A computer hacker learns from mysterious rebels..."
        );
        ReflectionTestUtils.setField(repairService, "rest", restTemplate);
        ReflectionTestUtils.setField(repairService, "apiKey", "test-key");

        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(details);

        // Run
        repairService.repairMetadata();

        // Verify: no links were deleted, no updates were made
        verify(repairHelper, never()).deleteLink(anyInt());
        verify(repairHelper, never()).updateMovieMetadata(anyInt(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void whenMovieIsMismatched_replacesWithCorrectLinkAndEnriches() {
        // Setup a mismatched movie: local "The Matrix (1999)" but linked to tmdbId 12345 (which is "Gravity")
        Movie movie = new Movie();
        movie.setMovieId(2);
        movie.setTitle("The Matrix");
        movie.setReleaseYear(1999);
        movie.setPosterUrl("https://image.tmdb.org/t/p/w342/gravity.jpg");
        movie.setDescription("Incorrect description");

        Link incorrectLink = new Link();
        incorrectLink.setMovie(movie);
        incorrectLink.setTmdbId(12345);
        movie.setLink(incorrectLink);

        when(movieRepository.findAllWithExternalLinks()).thenReturn(List.of(movie));

        // Mock TMDB Details for wrong tmdb_id (12345) -> returns Gravity
        Map<String, Object> wrongDetails = Map.of(
            "id", 12345,
            "title", "Gravity",
            "release_date", "2013-10-03",
            "overview", "Dr. Ryan Stone is a brilliant medical engineer..."
        );

        // Mock TMDB Search results for "The Matrix" (1999) -> returns correct tmdb_id (603) candidate
        Map<String, Object> searchResults = Map.of(
            "results", List.of(
                Map.of(
                    "id", 603,
                    "title", "The Matrix",
                    "release_date", "1999-03-30",
                    "overview", "A computer hacker learns from mysterious rebels..."
                )
            )
        );

        // Mock TMDB Details for correct tmdb_id (603)
        Map<String, Object> correctDetails = Map.of(
            "id", 603,
            "title", "The Matrix",
            "release_date", "1999-03-30",
            "overview", "A computer hacker learns from mysterious rebels...",
            "poster_path", "/matrix.jpg",
            "backdrop_path", "/matrix_backdrop.jpg"
        );

        // Mock TMDB Videos for correct tmdb_id (603)
        Map<String, Object> videoResults = Map.of(
            "results", List.of(
                Map.of(
                    "site", "YouTube",
                    "type", "Trailer",
                    "key", "m8e-FF8MsqU"
                )
            )
        );

        ReflectionTestUtils.setField(repairService, "rest", restTemplate);
        ReflectionTestUtils.setField(repairService, "apiKey", "test-key");

        // Set up RestTemplate expectations:
        // 1. Details for tmdb_id 12345
        when(restTemplate.getForObject(containsString("/movie/12345"), eq(Map.class))).thenReturn(wrongDetails);
        // 2. Search for "The Matrix"
        when(restTemplate.getForObject(containsString("/search/movie"), eq(Map.class))).thenReturn(searchResults);
        // 3. Details for correct tmdb_id 603
        when(restTemplate.getForObject(containsString("/movie/603?"), eq(Map.class))).thenReturn(correctDetails);
        // 4. Videos for correct tmdb_id 603
        when(restTemplate.getForObject(containsString("/movie/603/videos"), eq(Map.class))).thenReturn(videoResults);

        // Run
        repairService.repairMetadata();

        // Verify:
        // - Incorrect link deleted via helper
        verify(repairHelper).deleteLink(2);
        // - Correct link and metadata updated via helper
        verify(repairHelper).updateMovieMetadata(
            eq(2), eq(603),
            eq("https://image.tmdb.org/t/p/w342/matrix.jpg"),
            eq("https://image.tmdb.org/t/p/w780/matrix_backdrop.jpg"),
            eq("A computer hacker learns from mysterious rebels..."),
            eq("m8e-FF8MsqU")
        );
    }

    @Test
    void whenMovieHasNoLink_searchesAndEnrichesCorrectly() {
        // Setup a movie with no link
        Movie movie = new Movie();
        movie.setMovieId(3);
        movie.setTitle("Inception");
        movie.setReleaseYear(2010);

        when(movieRepository.findAllWithExternalLinks()).thenReturn(List.of(movie));

        // Mock TMDB Search results for "Inception" (2010)
        Map<String, Object> searchResults = Map.of(
            "results", List.of(
                Map.of(
                    "id", 27205,
                    "title", "Inception",
                    "release_date", "2010-07-15",
                    "overview", "Cobb, a skilled thief who commits corporate espionage..."
                )
            )
        );

        // Mock TMDB Details for correct tmdb_id (27205)
        Map<String, Object> correctDetails = Map.of(
            "id", 27205,
            "title", "Inception",
            "release_date", "2010-07-15",
            "overview", "Cobb, a skilled thief who commits corporate espionage...",
            "poster_path", "/inception.jpg",
            "backdrop_path", "/inception_backdrop.jpg"
        );

        // Mock TMDB Videos for correct tmdb_id (27205)
        Map<String, Object> videoResults = Map.of(
            "results", List.of(
                Map.of(
                    "site", "YouTube",
                    "type", "Trailer",
                    "key", "YoFYyK0kPdI"
                )
            )
        );

        ReflectionTestUtils.setField(repairService, "rest", restTemplate);
        ReflectionTestUtils.setField(repairService, "apiKey", "test-key");

        // Set up RestTemplate expectations:
        // 1. Search for "Inception"
        when(restTemplate.getForObject(containsString("/search/movie"), eq(Map.class))).thenReturn(searchResults);
        // 2. Details for correct tmdb_id 27205
        when(restTemplate.getForObject(containsString("/movie/27205?"), eq(Map.class))).thenReturn(correctDetails);
        // 3. Videos for correct tmdb_id 27205
        when(restTemplate.getForObject(containsString("/movie/27205/videos"), eq(Map.class))).thenReturn(videoResults);

        // Run
        repairService.repairMetadata();

        // Verify:
        // - No link was deleted
        verify(repairHelper, never()).deleteLink(anyInt());
        // - Correct link and metadata updated via helper
        verify(repairHelper).updateMovieMetadata(
            eq(3), eq(27205),
            eq("https://image.tmdb.org/t/p/w342/inception.jpg"),
            eq("https://image.tmdb.org/t/p/w780/inception_backdrop.jpg"),
            eq("Cobb, a skilled thief who commits corporate espionage..."),
            eq("YoFYyK0kPdI")
        );
    }

    @Test
    void whenMovieMatchesTmbdIdButMismatchedMetadata_correctsMismatchedFieldsInPlace() {
        // Setup a movie where the local title/year and TMDB ID are correct,
        // but local poster, backdrop, and description don't match what is on TMDB.
        Movie movie = new Movie();
        movie.setMovieId(4);
        movie.setTitle("Inception");
        movie.setReleaseYear(2010);
        movie.setPosterUrl("https://image.tmdb.org/t/p/w342/old_incorrect_poster.jpg");
        movie.setBackdropUrl("https://image.tmdb.org/t/p/w780/old_incorrect_backdrop.jpg");
        movie.setDescription("Old incorrect description");

        Link link = new Link();
        link.setMovie(movie);
        link.setTmdbId(27205);
        movie.setLink(link);

        when(movieRepository.findAllWithExternalLinks()).thenReturn(List.of(movie));

        // Mock TMDB response details with updated poster, backdrop, overview
        Map<String, Object> details = Map.of(
            "id", 27205,
            "title", "Inception",
            "release_date", "2010-07-15",
            "poster_path", "/new_inception_poster.jpg",
            "backdrop_path", "/new_inception_backdrop.jpg",
            "overview", "New correct overview"
        );
        ReflectionTestUtils.setField(repairService, "rest", restTemplate);
        ReflectionTestUtils.setField(repairService, "apiKey", "test-key");

        when(restTemplate.getForObject(containsString("/movie/27205?"), eq(Map.class))).thenReturn(details);

        // Run
        repairService.repairMetadata();

        // Verify: movie fields were updated and saved
        verify(movieRepository).save(movie);
        assertEquals("https://image.tmdb.org/t/p/w342/new_inception_poster.jpg", movie.getPosterUrl());
        assertEquals("https://image.tmdb.org/t/p/w780/new_inception_backdrop.jpg", movie.getBackdropUrl());
        assertEquals("New correct overview", movie.getDescription());
        assertEquals("TMDB", movie.getMetadataSource());
        assertNotNull(movie.getMetadataVerifiedAt());
        
        // No links should be deleted because TMDB ID is correct
        verify(repairHelper, never()).deleteLink(anyInt());
    }
    private String containsString(String sub) {
        return argThat(s -> s != null && s.contains(sub));
    }
}
